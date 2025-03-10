
from opentelemetry.proto.collector.trace.v1.trace_service_pb2 import ExportTraceServiceRequest
from google.protobuf.json_format import MessageToDict
import base64

from .models import Span

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


def otelScopedSpanToSpan(span: dict, service_name: str) -> Span:
    # Get fields
    trace_id = to_id(span.get('traceId', None), 16)
    span_id = to_id(span.get('spanId', None), 8)
    parent_span_id = to_id(span.get('parentSpanId', None), 8)

    trace_state = span.get('traceState', None)

    name = span.get('name', None)
    start_time = to_int(span.get('startTimeUnixNano', None))
    end_time = to_int(span.get('endTimeUnixNano', None))

    is_error, error_message = getErrorStatus(span)

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

    return span


def otelResourceSpanToSpans(scoped_spans) -> list[Span]:
    """Handle a single OTEL span"""
    span_resource_attributes = scoped_spans['resource']['attributes']
    service_name = get_attribute(span_resource_attributes, 'service.name')

    spans: list[Span] = []

    scope_spans = scoped_spans['scopeSpans']
    for scoped_spans in scope_spans:
        for scoped_span_entry in scoped_spans['spans']:
            spans.append(otelScopedSpanToSpan(scoped_span_entry, service_name))
    return spans


def otelTraceExportToSpans(data: dict) -> list[Span]:
    """Find and convert spans in single export"""
    spans: list[Span] = []
    for resource_span in data['resourceSpans']:
        spans.extend(otelResourceSpanToSpans(resource_span))
    return spans


def parse_otel_protobuf(data):
    """Parse OTEL protobuf data originating from a flask request"""
    request_proto = ExportTraceServiceRequest()
    request_proto.ParseFromString(data)
    return MessageToDict(request_proto)
