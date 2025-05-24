import argparse
import os
from graphviz import Digraph
import re
from dataclasses import dataclass, field, replace
from util import get_json, find_json
from tree_viz import simplify_signature

@dataclass(frozen=True)
class Point:
    signature: str
    destination: str
    count: int

    def any_sig(self) -> 'Point':
        return Point("*", self.destination, self.count)

    def any_count(self) -> 'Point':
        return Point(self.signature, self.destination, -1)
@dataclass(frozen=True)
class CallGraphNode:
    id: str
    stack: tuple[Point]
    children: list['CallGraphNode'] = field(default_factory=list)

def parse_point_str(name: str) -> tuple[Point]:
    name = re.sub(r"\{.+\}", "", name)
    parts = name.split(">")

    points: list[Point] = []
    for part in parts:
        n, c = part.split("#")
        count = int(c)
        parts = n.split(":")
        signature = "*"
        if len(parts) > 1:
            signature = simplify_signature(parts[1])
        points.append(Point(
            count=count,
            signature=signature,
            destination=parts[0],
        ))
    return tuple(points)

def parse_point(p: dict) -> tuple[Point]:
    points: list[Point] = []
    for ip in p['stack']:
        if ip["count"] < 0:
            return None
        sig = ip["signature"]
        sig = re.sub(r"\{.+\}", "", sig)
        points.append(Point(
            count=ip["count"],
            signature=simplify_signature(sig),
            destination=ip["destination"],
        ))
    return tuple(points)

def build_tree_from_mapping(node: tuple[Point], parent_children: dict[tuple[Point], list[Point]]):
    lookup_key = tuple([x.any_sig().any_count() for x in node])
    if lookup_key in parent_children:
        children = [build_tree_from_mapping(node + (x,), parent_children) for x in parent_children[lookup_key]]
    else:
        children = []
    
    point = node[-1]
    return CallGraphNode(
        id=f"{len(node)}-{point.signature}-{point.destination}",
        stack=node,
        children=children,
    )

def build_call_graph(pts: list, implications: list) -> CallGraphNode:
    stacks = [parse_point_str(p) if isinstance(p, str) else parse_point(p) for p in pts]
    stacks = [x for x in stacks if x != None]
    stacks.sort(key=lambda x: len(x))
    parent_children: dict[tuple[Point], list[Point]] = {}
    root = tuple([stacks[0][0].any_count()])
    parent_children.setdefault(root, [])

    for stack in stacks:
        point = stack[-1]
        parent_stack = stack[:-1]
        parent_key = tuple([x.any_sig().any_count() for x in parent_stack])
        parent_children.setdefault(parent_key, [])
        if point not in parent_children[parent_key]:
            parent_children[parent_key].append(point)

    for key in parent_children.keys():
        print(key)
        print(len(parent_children[key]))

    return build_tree_from_mapping(root, parent_children)

def construct_call_graph(dot: Digraph, node: CallGraphNode):
    point = node.stack[-1]
    label_parts = [
        point.destination,
        point.signature,
    ]
    label = "\n".join(label_parts)
    dot.node(node.id, label=label)

    for child in node.children:
        construct_call_graph(dot, child)
        dot.edge(node.id, child.id, label=f"#{child.stack[-1].count}")

def render_call_graph(data: dict, output_name: str):
    fault_injection_points = []
    if 'details' in data:
        if 'fault_injection_points' in data['details']:
            fault_injection_points = data['details']['fault_injection_points']
    implications = []

    if 'implications' in data:
        implications = data['implications']

    tree = build_call_graph(fault_injection_points, implications)
    dot = Digraph(comment='Call graph', format='pdf')
    construct_call_graph(dot, tree)
    dot.render(filename=output_name)


def get_args():
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument('dir_name', type=str,
                            help='Path to the experiment directory')
    arg_parser.add_argument('--nested', action='store_true',
                            help='Render all directories')

    args = arg_parser.parse_args()
    return args


if __name__ == '__main__':
    args = get_args()
    directory = args.dir_name
    if args.nested:
        for root, dirs, filenames in os.walk(directory):
            dirname = os.path.basename(root)
            if dirname != 'default' or 'timing.json' not in filenames:
                continue
            generator_data = get_json(find_json(root, "generator"))
            if generator_data is None:
                print(f"Generator data not found in {root}")
            else:
                render_call_graph(
                    generator_data, os.path.join(root, 'call_graph'))
    else:
        generator_data = get_json(find_json(directory, "generator"))
        if generator_data is None:
            print(f"Generator data not found in {directory}")
            exit(1)
        render_call_graph(generator_data, os.path.join(
            directory, 'call_graph'))
