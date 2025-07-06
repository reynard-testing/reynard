
import re
from dataclasses import dataclass, field

from tree_viz import simplify_signature


@dataclass(frozen=True)
class Call:
    signature: str | None
    destination: str | None
    count: int

    def any_sig(self) -> 'Call':
        return Call(None, self.destination, self.count)

    def any_count(self) -> 'Call':
        return Call(self.signature, self.destination, -1)


@dataclass(frozen=True)
class Point:
    call_stack: tuple[Call]

    def matches(self, other: 'Point') -> bool:
        if len(self.call_stack) != len(other.call_stack):
            return False

        for i in range(len(self.call_stack)):
            if self.call_stack[i].destination != other.call_stack[i].destination:
                return False
            if self.call_stack[i].signature is not None and other.call_stack[i].signature is not None:
                if self.call_stack[i].signature != other.call_stack[i].signature:
                    return False

        return True

    def parent(self) -> 'Point':
        if len(self.call_stack) == 0:
            return None
        return Point.from_calls(self.call_stack[:-1])

    def head(self) -> Call:
        if len(self.call_stack) == 0:
            raise IndexError("Point is empty")
        return self.call_stack[-1]

    @staticmethod
    def from_calls(calls: list[Call]) -> 'Point':
        if len(calls) == 0:
            return Point(tuple())
        return Point(tuple(calls))

    @staticmethod
    def parse(x: str | dict) -> 'Point':
        if isinstance(x, str):
            return parse_point_str(x)
        elif isinstance(x, dict):
            return parse_point(x)
        else:
            raise ValueError(f"Cannot parse point from {x}")


def parse_point_str(name: str) -> Point:
    if name.startswith("Behaviour["):
        name = re.sub(r"Behaviour\[(.+)\]", r"\1", name)
        uid_name, _ = name.split(", mode=")
        name = uid_name.replace("uid=", "")
    name = re.sub(r"\{.+\}", "", name)
    parts = name.split(">")

    points: list[Call] = []
    for part in parts:
        n, c = part.split("#")
        count = int(c)
        parts = n.split(":")
        signature = None

        if len(parts) > 1:
            signature = simplify_signature(parts[1])

        points.append(Call(
            count=count,
            signature=signature,
            destination=parts[0],
        ))

    return Point.from_calls(points)


def parse_point(p: dict) -> Point:
    if "mode" in p:
        return parse_point_str(p['uid'])

    points: list[Call] = []

    for ip in p['stack']:
        if ip["count"] < 0:
            return None
        sig = ip["signature"]
        sig = re.sub(r"\{.+\}", "", sig)
        points.append(Call(
            count=ip["count"],
            signature=simplify_signature(sig),
            destination=ip["destination"],
        ))

    return Point.from_calls(points)
