from dataclasses import dataclass

@dataclass(frozen=True)
class ResponseData:
    duration_ms: int
    status: int
    body: str


@dataclass(frozen=True)
class InjectionPoint:
    destination: str
    signature: str
    payload: str
    count: int


@dataclass(frozen=True)
class FaultUid:
    stack: tuple[InjectionPoint, ...]


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
class TraceReport:
    # Proxy reported data
    trace_id: str
    span_id: str
    uid: FaultUid
    is_initial: bool
    injected_fault: Fault
    concurrent_to: list[FaultUid]
    response: ResponseData
