import asyncio
import base64
from dataclasses import dataclass, field

import urllib

# import trace
from flask import Flask, request
from opentelemetry.proto.collector.trace.v1.trace_service_pb2 import ExportTraceServiceRequest
from google.protobuf.json_format import MessageToDict
import time

app = Flask(__name__)


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
    span_uid: str = None


@dataclass
class TraceNode:
    span: Span
    children: list['TraceNode'] = field(default_factory=list)


# Local in-memory storage
span_lookup: dict[str, Span] = {}
span_uid_lookup: dict[str, str] = {}
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
    span_id = to_id(span.get('spanId', None), 8)
    trace_id = to_id(span.get('traceId', None), 16)
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
        existing_span.span_uid = span_uid_lookup.get(span_id, None)
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
        span_uid=span_uid_lookup.get(span_id, None)
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
    tree_nodes = [TraceNode(span) for span in spans]
    span_lookup = {node.span.span_id: node for node in tree_nodes}

    # build tree
    for node in tree_nodes:
        parent = span_lookup.get(node.span.parent_span_id, None)
        if parent is None:
            continue
        parent.children.append(node)

    return [
        node for node in tree_nodes if span_lookup.get(node.span.parent_span_id, None) is None
    ]


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
    filtered_spans = [
        span for span in collected_spans if span.trace_id == trace_id]

    trees = get_trace_tree(filtered_spans)
    return {
        "spans": filtered_spans,
        "trees": trees
    }, 200


@app.route('/v1/link', methods=['POST'])
async def report_span_id():
    data = request.get_json()
    span_id = data.get('span_id')
    span_uid = data.get('span_uid')

    decoded_span_uid = urllib.parse.unquote(span_uid)
    span_uid_lookup[span_id] = decoded_span_uid

    if span_id in span_lookup:
        span_lookup[span_id].span_uid = decoded_span_uid

    return "OK", 200

if __name__ == '__main__':
    loop = asyncio.get_event_loop()
    loop.run_until_complete(app.run(host='0.0.0.0', port=5000))
