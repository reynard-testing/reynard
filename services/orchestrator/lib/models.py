from dataclasses import dataclass
from frozendict import frozendict


@dataclass(frozen=True)
class ResponseData:
    duration_s: float
    status: int
    body: str


@dataclass(frozen=True)
class PartialInjectionPoint:
    destination: str
    signature: str
    payload: str

    def to_str(self) -> str:
        payload_str = "" if not self.payload or self.payload == "*" else f"({self.payload})"
        return f"{self.destination}:{self.signature}{payload_str}"


@dataclass(frozen=True)
class InjectionPoint:
    destination: str
    signature: str
    payload: str
    vector_clock: frozendict[str, int]
    count: int

    def as_partial(self) -> PartialInjectionPoint:
        return PartialInjectionPoint(
            destination=self.destination,
            signature=self.signature,
            payload=self.payload,
        )


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
