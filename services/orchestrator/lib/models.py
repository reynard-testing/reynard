from dataclasses import dataclass, field


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
    status: int
    body: str


@dataclass
class FaultUid:
    origin: str
    destination: str
    signature: str
    payload: str
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
    trace_id: str
    span_id: str
    uid: FaultUid
    injected_fault: Fault
    response: ResponseData


@dataclass
class TraceTreeNode:
    span: Span
    report: ReportedSpan
    children: list['TraceTreeNode'] = field(default_factory=list)
