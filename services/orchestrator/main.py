import asyncio
import os

import requests

# import trace
from flask import Flask, request

from lib.models import Span, ReportedSpan, ResponseData, FaultUid, FaultMode, Fault, Faultload, TraceTreeNode
from lib.otel import otelTraceExportToSpans, parse_otel_protobuf
from lib.span_store import SpanStore
from lib.report_store import ReportStore

app = Flask(__name__)

proxy_list: list[str] = [proxy for proxy in os.getenv(
    'PROXY_LIST', '').split(',') if proxy]
debug_flag_set = os.getenv('DEBUG', "true").lower() == "true"

# Local in-memory storage
span_store = SpanStore()
report_store = ReportStore()
trace_ids: set[str] = set()

CLIENT_ROOT_SPAN_ID = "0000000000000001"

debug_all_spans: list[Span] = []
debug_raw_exports: list = []


def to_trace_tree_nodes(spans: list[Span]) -> list[TraceTreeNode]:
    return [TraceTreeNode(span, report_store.get_by_span_id(span.span_id)) for span in spans]


def get_trace_tree(spans: list[Span]) -> list[TraceTreeNode]:
    """Convert spans to a trace tree (list of root nodes)"""
    # convert to nodes & build lookup
    tree_nodes = to_trace_tree_nodes(spans)
    tree_node_by_span_id = {node.span.span_id: node for node in tree_nodes}

    # build tree
    for node in tree_nodes:
        # find parent
        parent = tree_node_by_span_id.get(node.span.parent_span_id, None)
        if parent is None:
            continue

        # add node as child of parent
        parent.children.append(node)

    # find root nodes
    root_spans = [
        node for node in tree_nodes if tree_node_by_span_id.get(node.span.parent_span_id, None) is None
    ]

    # if there is only one root, return it
    if len(root_spans) <= 1:
        return root_spans

    # > 1 roots
    # filter out root spans that have no children
    root_spans = [x for x in root_spans if len(x.children) > 0]

    return root_spans


def get_trace_tree_for_trace(trace_id: str) -> tuple[list[Span], list[TraceTreeNode]]:
    spans = span_store.get_by_trace_id(trace_id)

    # If the clients sends a fictional root span, add it to build the correct tree
    needs_client_root_span = any(
        span.parent_span_id == CLIENT_ROOT_SPAN_ID for span in spans)

    if needs_client_root_span:
        root_span = Span(
            span_id=CLIENT_ROOT_SPAN_ID,
            trace_id=trace_id,
            parent_span_id=None,
            name="Client Root Span",
            start_time=0,
            end_time=0,
            service_name="Client",
            trace_state=None,
            is_error=False,
            error_message=None,
        )
        spans.append(root_span)

    # convert to tree
    return spans, get_trace_tree(spans)


def get_report_tree_children(children: list[TraceTreeNode]) -> list[TraceTreeNode]:
    res = []
    for child in children:
        res += get_report_tree(child)
    return res


def get_report_tree(node: TraceTreeNode) -> list[TraceTreeNode]:
    is_report_node = node.report != None or node.span.span_id == CLIENT_ROOT_SPAN_ID

    if is_report_node:
        return [TraceTreeNode(
            span=node.span,
            report=node.report,
            children=get_report_tree_children(node.children)
        )]

    return get_report_tree_children(node.children)

# ----------------- API Endpoints -----------------


@app.route('/v1/traces', methods=['POST'])
def collect():
    """OTEL span export collection endpoint"""
    # parse data
    raw_data = request.data
    data_dict = parse_otel_protobuf(raw_data)

    # store raw data for debugging
    if debug_flag_set:
        debug_raw_exports.append(data_dict)

    spans = otelTraceExportToSpans(data_dict)
    for span in spans:
        # store all spans for debugging
        if debug_flag_set:
            debug_all_spans.append(span)

        # ignore spans that are not part of a trace of interest
        if span.trace_id not in trace_ids:
            continue

        # if the span already exists, update it
        existing_span = span_store.get_by_span_id(span.span_id)
        if existing_span is not None:
            # update existing span and return
            existing_span.is_error = span.is_error
            existing_span.error_message = span.error_message
            existing_span.end_time = span.end_time
            continue

        # otherwise, add the span
        span_store.add(span)

    return "Data collected", 200

# --- DEBUG ENDPOINTS ---


@app.route('/v1/debug/all-trees', methods=['GET'])
def debug_get_all_span_trees():
    trees = get_trace_tree(debug_all_spans)
    return {
        "spans": debug_all_spans,
        "trees": trees
    }, 200


@app.route('/v1/debug/raw', methods=['GET'])
def debug_get_all_exports():
    return {
        "data": debug_raw_exports,
    }, 200

# --- DATA ENDPOINTS ---


@app.route('/v1/trace/<trace_id>', methods=['GET'])
def get_all_by_trace_id(trace_id):
    spans, trees = get_trace_tree_for_trace(trace_id)

    reports = report_store.get_by_trace_id(trace_id)
    report_tree = get_report_tree(trees[0]) if len(trees) == 1 else None

    return {
        "spans": spans,
        "reports": reports,
        "report_trees": report_tree,
        "trees": trees
    }, 200


@app.route('/v1/trace/<trace_id>/trees', methods=['GET'])
def get_trees_by_trace_id(trace_id):
    _, trees = get_trace_tree_for_trace(trace_id)
    return trees, 200


@app.route('/v1/trace/<trace_id>/report-trees', methods=['GET'])
def get_report_tree_by_trace_id(trace_id):
    _, trees = get_trace_tree_for_trace(trace_id)

    if len(trees) != 1:
        return [], 404

    report_tree = get_report_tree(trees[0])
    return report_tree, 200


@app.route('/v1/trace/<trace_id>/reports', methods=['GET'])
def get_reports_by_trace_id(trace_id):
    reports = report_store.get_by_trace_id(trace_id)
    return reports, 200


# --- LINK/REPORT ENDPOINTS ---

@app.route('/v1/link', methods=['POST'])
async def report_span_id():
    data = request.get_json()

    trace_id = data.get('trace_id')
    span_id = data.get('span_id')
    uid = data.get('uid')
    injected_fault = data.get('injected_fault')
    response = data.get('response')

    if trace_id not in trace_ids:
        return "Trace not registered", 404

    responseData = None
    if response:
        responseData = ResponseData(
            status=response.get('status'),
            body=response.get('body')
        )

    faultUid = FaultUid(
        origin=uid.get('origin'),
        signature=uid.get('signature'),
        count=uid.get('count'),
        destination=uid.get('destination'),
        payload=uid.get('payload'),
    )

    span_report = ReportedSpan(
        trace_id=trace_id,
        span_id=span_id,
        uid=faultUid,
        injected_fault=injected_fault,
        response=responseData,
    )

    if report_store.has_span_id(span_id):
        existing_report = report_store.get_by_span_id(span_id)
        existing_report.response = responseData
        print("Updated reported span", span_report, flush=True)
    else:
        report_store.add(span_report)
        print("Added reported span", span_report, flush=True)

    return "OK", 200

# --- FAULTLOAD (UN)REGISTER ENDPOINTS ---


async def register_faultload_at_proxy(proxy: str, payload):
    url = f"http://{proxy}/v1/faultload/register"
    response = requests.post(url, json=payload)

    if response.status_code != 200:
        raise Exception(
            f"Failed to register faultload at proxy {proxy}: {response.status_code} {response.text}")


@app.route("/v1/faultload/register", methods=['POST'])
async def register_faultload():
    payload = request.get_json()
    trace_id = payload.get('trace_id')
    trace_ids.add(trace_id)

    tasks = [register_faultload_at_proxy(
        proxy, payload) for proxy in proxy_list]
    await asyncio.gather(*tasks)

    return "OK", 200


async def unregister_faultload_at_proxy(proxy: str, payload):
    url = f"http://{proxy}/v1/faultload/unregister"
    response = requests.post(url, json=payload)

    if response.status_code != 200:
        raise Exception(
            f"Failed to register faultload at proxy {proxy}: {response.status_code} {response.text}")


@app.route("/v1/faultload/unregister", methods=['POST'])
async def unregister_faultload():
    payload = request.get_json()
    trace_id = payload.get('trace_id')
    trace_ids.remove(trace_id)

    tasks = [unregister_faultload_at_proxy(
        proxy, payload) for proxy in proxy_list]
    await asyncio.gather(*tasks)

    return "OK", 200

print("Registered proxies: ", proxy_list, flush=True)
print("Debug?: ", debug_flag_set, flush=True)
if __name__ == '__main__':
    print("Starting orchestrator", flush=True)
    loop = asyncio.get_event_loop()
    loop.run_until_complete(app.run(host='0.0.0.0', port=5000))
