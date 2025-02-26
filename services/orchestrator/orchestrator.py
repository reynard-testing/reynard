import asyncio
import base64
import os
from dataclasses import dataclass, field

import requests

# import trace
from flask import Flask, request
from opentelemetry.proto.collector.trace.v1.trace_service_pb2 import ExportTraceServiceRequest
from google.protobuf.json_format import MessageToDict

app = Flask(__name__)

trace_ids: set[str] = set()

proxy_list: list[str] = [proxy for proxy in os.getenv(
    'PROXY_LIST', '').split(',') if proxy]


@dataclass
class Span:
    span_id: str
    trace_id: str
    parent_span_id: str
    name: str
    start_time: int
    end_time: int
    service_name: str
    trace_state: str
    is_error: bool
    error_message: str


@dataclass
class ResponseData:
    # Proxy reported data
    status: int
    body: str


@dataclass
class FaultUid:
    origin: str
    destination: str
    signature: str
    count: int


@dataclass
class FaultMode:
    type: str
    args: list[str]


@dataclass
class Fault:
    uid: FaultUid
    mode: FaultMode


@dataclass
class Faultload:
    # Faultload data
    faults: list[Fault]
    trace_id: str


@dataclass
class ReportedSpan:
    # Proxy reported data
    span_id: str
    uid: FaultUid
    injected_fault: Fault
    response: ResponseData


@dataclass
class TraceNode:
    span: Span
    report: ReportedSpan
    children: list['TraceNode'] = field(default_factory=list)


# Local in-memory storage
span_lookup: dict[str, Span] = {}
span_report_lookup: dict[str, ReportedSpan] = {}

collected_spans: list[Span] = []
collected_raw: list = []


def find_span_by_id(span_id):
    return span_lookup.get(span_id, None)


def add_span(span: Span):
    collected_spans.append(span)
    span_lookup[span.span_id] = span


# -- Helper functions for OTEL data --
def get_attribute(attributes, key):
    for attr in attributes:
        if attr['key'] == key:
            value = attr['value']
            if isinstance(value, dict) and 'stringValue' in value:
                return value['stringValue']
            return value
    return None


def to_id(base_id, byte_length=16):
    if base_id is None:
        return None

    as_int = int.from_bytes(base64.b64decode(base_id), 'big')
    return hex(as_int)[2:].zfill(byte_length * 2)


def to_int(value):
    if value is None:
        return None
    return int(value)


def getErrorStatus(span: dict) -> tuple[bool, str]:
    status = span.get('status', None)
    if status is None:
        return None, None

    code = status.get('code', None)
    if code is None:
        return None, None

    errenous = code == "STATUS_CODE_ERROR"

    return errenous, status.get('message', None)

# handle a single OTEL span


def handleScopeSpan(span: dict, service_name: str):
    # Get fields
    trace_id = to_id(span.get('traceId', None), 16)

    if trace_id not in trace_ids:
        return

    span_id = to_id(span.get('spanId', None), 8)
    parent_span_id = to_id(span.get('parentSpanId', None), 8)

    trace_state = span.get('traceState', None)

    name = span.get('name', None)
    start_time = to_int(span.get('startTimeUnixNano', None))
    end_time = to_int(span.get('endTimeUnixNano', None))

    is_error, error_message = getErrorStatus(span)

    # update existing span if it exists
    existing_span = find_span_by_id(span_id)

    if existing_span is not None:
        # update existing span and return
        existing_span.is_error = is_error
        existing_span.error_message = error_message
        existing_span.end_time = end_time
        return

    # create NEW span
    span = Span(
        span_id=span_id,
        trace_id=trace_id,
        parent_span_id=parent_span_id,
        name=name,
        start_time=start_time,
        end_time=end_time,
        service_name=service_name,
        trace_state=trace_state,
        is_error=is_error,
        error_message=error_message,
    )

    add_span(span)


def handleSpan(span):
    span_resource_attributes = span['resource']['attributes']
    service_name = get_attribute(span_resource_attributes, 'service.name')

    scope_spans = span['scopeSpans']
    for span in scope_spans:
        for scopedspan in span['spans']:
            handleScopeSpan(scopedspan, service_name)


def parse_protobuf(data):
    request_proto = ExportTraceServiceRequest()
    request_proto.ParseFromString(data)
    return MessageToDict(request_proto)


@app.route('/v1/traces', methods=['POST'])
def collect():
    raw_data = request.data
    data_dict = parse_protobuf(raw_data)
    collected_raw.append(data_dict)

    for span in data_dict['resourceSpans']:
        handleSpan(span)

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
    return {
        "spans": spans,
        "trees": trees
    }, 200


@app.route('/v1/get-trees/<trace_id>', methods=['GET'])
def get_tree_by_trace_id(trace_id):
    _, trees = get_trace_tree_for_trace(trace_id)
    return trees, 200


@app.route('/v1/link', methods=['POST'])
async def report_span_id():
    data = request.get_json()
    span_id = data.get('span_id')
    uid = data.get('uid')
    injected_fault = data.get('injected_fault')
    response = data.get('response')
    responseData = ResponseData(
        status=response.get('status'),
        body=response.get('body')
    ) if response else None

    span_report = ReportedSpan(
        span_id=span_id,
        uid=FaultUid(
            origin=uid.get('origin'),
            signature=uid.get('signature'),
            count=uid.get('count'),
            destination=uid.get('destination'),
        ),
        injected_fault=injected_fault,
        response=responseData,
    )
    print("Found reported span", span_report, flush=True)

    span_report_lookup[span_id] = span_report
    return "OK", 200


async def register_faultload_at_proxy(proxy: str, payload):
    url = f"http://{proxy}/v1/register_faultload"
    response = requests.post(url, json=payload)

    if response.status_code != 200:
        raise Exception(
            f"Failed to register faultload at proxy {proxy}: {response.status_code} {response.text}")


@app.route("/v1/register_faultload", methods=['POST'])
async def register_faultload():
    payload = request.get_json()
    trace_id = payload.get('trace_id')
    trace_ids.add(trace_id)

    tasks = [register_faultload_at_proxy(
        proxy, payload) for proxy in proxy_list]
    await asyncio.gather(*tasks)

    return "OK", 200

if __name__ == '__main__':
    print("Starting orchestrator")
    print("Registered proxies: ", proxy_list)
    loop = asyncio.get_event_loop()
    loop.run_until_complete(app.run(host='0.0.0.0', port=5000))
