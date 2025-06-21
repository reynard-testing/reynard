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


@dataclass
class Dependency:
    dependent: tuple[Point]
    dependency: list['tuple[Point]'] = field(default_factory=list)
    inclusion: bool = True


@dataclass(frozen=True)
class CallGraphNode:
    id: str
    stack: tuple[Point]
    children: list['CallGraphNode'] = field(default_factory=list)
    inclusion_cond: list['Dependency'] = field(default_factory=list)
    exclusion_cond: list['Dependency'] = field(default_factory=list)


def parse_pt(x) -> tuple[Point]:
    if isinstance(x, str):
        return parse_point_str(x)
    return parse_point(x)


def parse_point_str(name: str) -> tuple[Point]:
    if name.startswith("Behaviour["):
        name = re.sub(r"Behaviour\[(.+)\]", r"\1", name)
        uid_name, _ = name.split(", mode=")
        name = uid_name.replace("uid=", "")
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
    if "mode" in p:
        return parse_point_str(p['uid'])
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


def to_identifier(node: tuple[Point]) -> str:
    pt = node[-1]
    return f"{len(node)}-{pt.signature}-{pt.destination}"


def points_match(a: tuple[Point], b: tuple[Point]) -> bool:
    if len(a) != len(b):
        return False
    for i in range(len(a)):
        if a[i].destination != b[i].destination:
            return False
        if a[i].signature != "*" and b[i].signature != "*":
            if a[i].signature != b[i].signature:
                return False
    return True


def remove_transitive_dependencies(relations: list[tuple[str, str]]) -> list[tuple[str, str]]:
    if not relations:
        return []

    # Convert relations to a set for efficient lookup
    relations_set = set(relations)

    # Store the simplified relations
    simplified_relations = set()

    # Iterate through all relations to check for transitivity
    for r_from, r_to in relations_set:
        is_transitive = False

        # Check if there's an intermediate node 'k' that makes (r_from, r_to) transitive
        # This means checking if (r_from, k) and (k, r_to) both exist
        for k_from, k_to in relations_set:
            # Found a direct path (r_from, k_to)
            if k_from == r_from and k_to != r_to:
                if (k_to, r_to) in relations_set:  # And a path from k_to to r_to
                    is_transitive = True
                    break

        if not is_transitive:
            simplified_relations.add((r_from, r_to))

    return sorted(list(simplified_relations))


def build_tree_from_mapping(node: tuple[Point], parent_children: dict[tuple[Point], list[Point]], dependencies: list[Dependency]):
    lookup_key = tuple([x.any_sig().any_count() for x in node])
    if lookup_key in parent_children:
        children = [build_tree_from_mapping(
            node + (x,), parent_children, dependencies) for x in parent_children[lookup_key]]
    else:
        children = []

    related_dependencies = [
        d for d in dependencies if points_match(d.dependent[:-1], node)]

    return CallGraphNode(
        id=to_identifier(node),
        stack=node,
        children=children,
        inclusion_cond=[x for x in related_dependencies if x.inclusion],
        exclusion_cond=[x for x in related_dependencies if not x.inclusion],
    )


def build_call_graph(stacks: list[tuple[Point]], dependencies: list[Dependency]) -> tuple[CallGraphNode, list[tuple[Point]]]:
    stacks.sort(key=lambda x: len(x))
    parent_children: dict[tuple[Point], list[Point]] = {}
    if len(stacks) == 0 or len(stacks[0]) == 0:
        print("No points found")
        print(stacks)
        return None, []
    root = tuple([stacks[0][0].any_count()])
    parent_children.setdefault(root, [])

    for stack in stacks:
        point = stack[-1]
        parent_stack = stack[:-1]
        parent_key = tuple([x.any_sig().any_count() for x in parent_stack])
        parent_children.setdefault(parent_key, [])
        if point not in parent_children[parent_key]:
            parent_children[parent_key].append(point)

    return build_tree_from_mapping(root, parent_children, dependencies), stacks


def construct_call_graph(dot: Digraph, node: CallGraphNode, use_signature: bool = True, use_dependency: bool = False):
    point = node.stack[-1]
    label_parts = [
        point.destination,
    ]
    if use_signature:
        label_parts.append(point.signature)
    label = "\n".join(label_parts)
    dot.node(node.id, label=label)

    for child in node.children:
        construct_call_graph(dot, child, use_signature, use_dependency)
        dot.edge(node.id, child.id,
                 label=f"#{child.stack[-1].count}")

    if not use_dependency:
        return

    exclusion_relations = []
    for exc in node.exclusion_cond:
        for dep in exc.dependency:
            exclusion_relations.append(
                (to_identifier(exc.dependent), to_identifier(dep)))

    local_exclusions = remove_transitive_dependencies(exclusion_relations)
    for (n, e) in local_exclusions:
        dot.edge(e, n, style='dashed', constraint='false')

    inclusion_relations = []
    for inc in node.inclusion_cond:
        for dep in inc.dependency:
            relation = (to_identifier(inc.dependent), to_identifier(dep))
            if relation in exclusion_relations:
                continue
            inclusion_relations.append(relation)

    local_inclusions = remove_transitive_dependencies(inclusion_relations)
    for (n, e) in local_inclusions:
        dot.edge(e, n, style='dashed', color='red', constraint='false')


def should_use_signature(pts: list[tuple[Point]]) -> bool:
    ids: dict[str, str] = {}
    for p in pts:
        identifier = p[-1].destination + str(p[-1].count)
        if identifier in ids:
            if ids[identifier] != p[-1].signature:
                return True
        ids[identifier] = p[-1].signature
    return False


def render_call_graph(data: dict, output_name: str):
    points = []
    if 'details' in data:
        if 'fault_injection_points' in data['details']:
            points = data['details']['fault_injection_points']

    points = [parse_pt(p) for p in points]
    points = [x for x in points if x != None]

    dependencies: list[Dependency] = []
    if 'implications' in data:
        if 'inclusions' in data['implications']:
            for inc in data['implications']['inclusions']['list']:
                effect = parse_pt(inc['effect_name'])
                causes = [parse_pt(c) for c in inc['causes_names']]
                dependencies.append(Dependency(
                    dependent=effect,
                    dependency=causes,
                    inclusion=True,
                ))
        if 'exclusions' in data['implications']:
            for exc in data['implications']['exclusions']['list']:
                effect = parse_pt(exc['effect_name'])
                causes = [parse_pt(c) for c in exc['causes_names']]
                dependencies.append(Dependency(
                    dependent=effect,
                    dependency=causes,
                    inclusion=False,
                ))

    tree, pts = build_call_graph(points, dependencies)
    if tree is None:
        print("No call graph found")
        return
    use_signature = should_use_signature(pts)
    dot = Digraph(comment='Call graph', format='pdf')
    dot.attr(rankdir='LR')
    construct_call_graph(dot, tree, use_signature, False)
    dot.render(filename=output_name)

    dot_dep = Digraph(comment='Call graph with dependencies', format='pdf')
    dot_dep.attr(rankdir='LR')
    construct_call_graph(dot_dep, tree, use_signature, True)
    dot_dep.render(filename=output_name + "_dependency")


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
