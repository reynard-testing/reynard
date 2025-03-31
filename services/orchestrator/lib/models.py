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


@dataclass(frozen=True)
class ResponseData:
    status: int
    body: str


@dataclass(frozen=True)
class FaultUid:
    origin: str
    destination: str
    signature: str
    payload: str
    count: int


@dataclass(frozen=True)
class FaultMode:
    type: str
    args: list[str]


@dataclass(frozen=True)
class Fault:
    uid: FaultUid
    mode: FaultMode


@dataclass(frozen=True)
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
    is_initial: bool
    injected_fault: Fault
    concurrent_to: list[FaultUid]
    response: ResponseData


@dataclass
class TraceTreeNode:
    span: Span
    reports: list['ReportedSpan'] = field(default_factory=list)
    children: list['TraceTreeNode'] = field(default_factory=list)
