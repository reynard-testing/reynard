import asyncio
import os

import requests

# import trace
from flask import Flask, request

from lib.models import Span, ReportedSpan, ResponseData, FaultUid, FaultMode, Fault, Faultload, TraceNode
from lib.otel import otelTraceExportToSpans, parse_otel_protobuf
from lib.span_store import SpanStore

app = Flask(__name__)

trace_ids: set[str] = set()

proxy_list: list[str] = [proxy for proxy in os.getenv(
    'PROXY_LIST', '').split(',') if proxy]


# Local in-memory storage
span_store = SpanStore()
span_report_lookup: dict[str, ReportedSpan] = {}
trace_report_lookup: dict[str, list[ReportedSpan]] = {}

collected_spans: list[Span] = []
collected_raw: list = []


@app.route('/v1/traces', methods=['POST'])
def collect():
    # parse data
    raw_data = request.data
    data_dict = parse_otel_protobuf(raw_data)

    # store raw data
    collected_raw.append(data_dict)

    spans = otelTraceExportToSpans(data_dict)
    for span in spans:
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


def get_trace_tree(spans: list[Span]):

    # convert to nodes & build lookup
    tree_nodes = [TraceNode(span, span_report_lookup.get(
        span.span_id, None)) for span in spans]
    span_lookup = {node.span.span_id: node for node in tree_nodes}

    # build tree
    for node in tree_nodes:
        parent = span_lookup.get(node.span.parent_span_id, None)
        if parent is None:
            continue
        parent.children.append(node)

    root_spans = [
        node for node in tree_nodes if span_lookup.get(node.span.parent_span_id, None) is None
    ]

    if len(root_spans) <= 1:
        return root_spans

    # > 1 roots
    root_spans = [x for x in root_spans if len(x.children) > 0]
    return root_spans


def get_trace_tree_for_trace(trace_id: str):
    filtered_spans = [
        span for span in collected_spans if span.trace_id == trace_id]

    # If the clients sends a fictional root span, add it to build the correct tree
    root_span_id = "0000000000000001"
    has_client_root_span = any(
        span.parent_span_id == root_span_id for span in filtered_spans)
    if has_client_root_span:
        root_span = Span(
            span_id=root_span_id,
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
        filtered_spans.append(root_span)
    return filtered_spans, get_trace_tree(filtered_spans)


@app.route('/v1/all', methods=['GET'])
def get_spans():
    trees = get_trace_tree(collected_spans)
    return {
        "spans": collected_spans,
        "trees": trees
    }, 200


@app.route('/v1/raw', methods=['GET'])
def get_raw_spans():
    return {
        "data": collected_raw,
    }, 200


@app.route('/v1/get/<trace_id>', methods=['GET'])
def get_spans_by_trace_id(trace_id):
    spans, trees = get_trace_tree_for_trace(trace_id)

    reports = trace_report_lookup.get(trace_id)
    report_tree = get_report_tree(trees[0]) if len(trees) == 1 else None

    return {
        "spans": spans,
        "reports": reports,
        "report_trees": report_tree,
        "trees": trees
    }, 200


@app.route('/v1/get-trees/<trace_id>', methods=['GET'])
def get_tree_by_trace_id(trace_id):
    _, trees = get_trace_tree_for_trace(trace_id)
    return trees, 200


def get_report_tree_children(children: list[TraceNode]) -> list[TraceNode]:
    res = []
    for child in children:
        res += get_report_tree(child)
    return res


def get_report_tree(node: TraceNode) -> list[TraceNode]:
    is_report_node = node.report != None or node.span.span_id == "0000000000000001"

    if is_report_node:
        return [TraceNode(
            span=node.span,
            report=node.report,
            children=get_report_tree_children(node.children)
        )]
    return get_report_tree_children(node.children)


@app.route('/v1/get-report-tree/<trace_id>', methods=['GET'])
def get_report_tree_by_trace_id(trace_id):
    _, trees = get_trace_tree_for_trace(trace_id)

    if len(trees) != 1:
        return [], 404

    report_tree = get_report_tree(trees[0])
    return report_tree, 200


@app.route('/v1/get-reports/<trace_id>', methods=['GET'])
def get_reports_by_trace_id(trace_id):
    reports = trace_report_lookup.get(trace_id)
    return reports, 200


@app.route('/v1/link', methods=['POST'])
async def report_span_id():
    data = request.get_json()
    span_id = data.get('span_id')
    trace_id = data.get('trace_id')
    uid = data.get('uid')
    injected_fault = data.get('injected_fault')
    response = data.get('response')
    responseData = ResponseData(
        status=response.get('status'),
        body=response.get('body')
    ) if response else None

    faultUid = FaultUid(
        origin=uid.get('origin'),
        signature=uid.get('signature'),
        count=uid.get('count'),
        destination=uid.get('destination'),
        payload=uid.get('payload'),
    )

    span_report = ReportedSpan(
        span_id=span_id,
        uid=faultUid,
        injected_fault=injected_fault,
        response=responseData,
    )
    print("Found reported span", span_report, flush=True)

    span_report_lookup[span_id] = span_report
    trace_list = trace_report_lookup.setdefault(trace_id, [])
    # TODO: replace existing report
    trace_list.append(span_report)
    return "OK", 200


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

if __name__ == '__main__':
    print("Starting orchestrator")
    print("Registered proxies: ", proxy_list)
    loop = asyncio.get_event_loop()
    loop.run_until_complete(app.run(host='0.0.0.0', port=5000))
