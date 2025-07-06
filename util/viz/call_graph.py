import argparse
import hashlib
import os
from dataclasses import dataclass, field

from graphviz import Digraph
from reynard_viz.points import Call, Point
from reynard_viz.util import find_json, get_json


@dataclass
class Dependency:
    dependent: Point
    dependency: list['Point'] = field(default_factory=list)
    inclusion: bool = True


@dataclass(frozen=True)
class CallGraphNode:
    id: str
    point: Point
    children: list['CallGraphNode'] = field(default_factory=list)
    inclusion_cond: list['Dependency'] = field(default_factory=list)
    exclusion_cond: list['Dependency'] = field(default_factory=list)
    exclusion_relation: list[tuple[str, str]] = field(default_factory=list)
    inclusion_relation: list[tuple[str, str]] = field(default_factory=list)


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

    return sorted(simplified_relations)


def to_minimal_dep(root: Point, deps: list[Dependency]) -> list[tuple[str, str]]:
    # [source,destination] = 1
    rel: dict[tuple[str, str]] = {}

    def from_root(p: Point) -> Point:
        return Point.from_calls(list(root.call_stack) + [p.head()])

    for d in deps:
        to_id = to_identifier(from_root(d.dependent))
        for dep in d.dependency:
            from_id = to_identifier(from_root(dep))
            key = (from_id, to_id)
            if key in rel:
                continue
            rel[key] = 1
    relation = list(rel.keys())
    # remove transitive dependencies
    return remove_transitive_dependencies(relation)


def to_identifier(node: Point) -> str:
    base_str = "|".join(
        f"{p.destination}_{p.signature}#{p.count}" for p in node.call_stack)
    safe_str = base_str.replace("/", "_").replace("\n", "")
    hash_str = hashlib.sha256(safe_str.encode()).hexdigest()
    return hash_str


def as_rel_id(id: str) -> str:
    return f"{id}_rel"


def build_tree_from_mapping(node: Point, children_by_parent: dict[Point, list[Call]], dependencies: list[Dependency]):
    child_calls = children_by_parent.get(node, [])
    children = [build_tree_from_mapping(
        Point.from_calls(list(node.call_stack) + [x]), children_by_parent, dependencies) for x in child_calls]

    related_dependencies = [
        d for d in dependencies if d.dependent.parent().matches(node)]

    exclusion_cond = [x for x in related_dependencies if not x.inclusion]
    inclusion_cond = [x for x in related_dependencies if x.inclusion]

    min_inclusion_cond = to_minimal_dep(node, inclusion_cond)
    min_exclusion_cond = to_minimal_dep(node, exclusion_cond)
    min_exclusion_cond = [
        r for r in min_exclusion_cond if r not in min_inclusion_cond]

    return CallGraphNode(
        id=to_identifier(node),
        point=node,
        children=children,
        exclusion_cond=exclusion_cond,
        inclusion_cond=inclusion_cond,
        exclusion_relation=min_exclusion_cond,
        inclusion_relation=min_inclusion_cond,
    )


def build_call_graph(points: list[Point], dependencies: list[Dependency]) -> tuple[CallGraphNode, list[Point]]:
    # If there is no root point, return None
    if len(points) == 0:
        print("No points found")
        print(points)
        return None, []

    # Sort ports by the length of their call stack
    root = Point.from_calls([points[0].call_stack[0]])

    children_by_parent: dict[Point, list[Call]] = {}
    children_by_parent.setdefault(root, [])

    for point in points:
        parent = point.parent()
        if parent is None:
            continue

        final_call = point.head()
        children_by_parent.setdefault(parent, [])
        if point not in children_by_parent[parent]:
            children_by_parent[parent].append(final_call)

    return build_tree_from_mapping(root, children_by_parent, dependencies), points


def construct_call_graph(dot: Digraph, node: CallGraphNode, use_dependency: bool = False):
    call = node.point.head()

    dot.node(node.id, label=call.destination)
    node_rel_id = as_rel_id(node.id)
    dot.node(node_rel_id, label="",
             shape="point",
             fixedsize="true",
             width="0",
             style="invis"
             )

    edge_label = f"{call.signature}#{call.count}"

    dot.edge(node_rel_id, node.id, label=edge_label)

    for child in node.children:
        construct_call_graph(dot, child, use_dependency)
        dot.edge(node.id, as_rel_id(child.id), arrowhead="none")

    if not use_dependency:
        return

    for (from_dep, to_dep) in node.exclusion_relation:
        x = as_rel_id(from_dep)
        y = as_rel_id(to_dep)
        dot.edge(x, y, style='dashed', color="gray", constraint='false')

    for (from_dep, to_dep) in node.inclusion_relation:
        x = as_rel_id(from_dep)
        y = as_rel_id(to_dep)
        dot.edge(x, y, color='red', constraint='false')


def get_points_from_data(data: dict) -> list[Point]:
    points = []
    if 'details' in data and 'fault_injection_points' in data['details']:
        points = data['details']['fault_injection_points']

    points = [Point.parse(p) for p in points]
    return [x for x in points if x is not None]


def get_dependencies_from_data(data: dict) -> list[Dependency]:
    dependencies: list[Dependency] = []
    if 'implications' in data:
        if 'inclusions' in data['implications']:
            for inc in data['implications']['inclusions']['list']:
                effect = Point.parse(inc['effect_name'])
                causes = [Point.parse(c) for c in inc['causes_names']]
                dependencies.append(Dependency(
                    dependent=effect,
                    dependency=causes,
                    inclusion=True,
                ))

        if 'exclusions' in data['implications']:
            for exc in data['implications']['exclusions']['list']:
                effect = Point.parse(exc['effect_name'])
                causes = [Point.parse(c) for c in exc['causes_names']]
                dependencies.append(Dependency(
                    dependent=effect,
                    dependency=causes,
                    inclusion=False,
                ))

    return dependencies


def render_call_graph(data: dict, output_name: str):
    points = get_points_from_data(data)
    dependencies = get_dependencies_from_data(data)

    tree, pts = build_call_graph(points, dependencies)
    if tree is None:
        print("No call graph found")
        return

    dot = Digraph(comment='Call graph', format='pdf')
    dot.attr(rankdir='LR')
    construct_call_graph(dot, tree, False)
    dot.render(filename=output_name)

    dot_dep = Digraph(comment='Call graph with dependencies', format='pdf')
    dot_dep.attr(rankdir='LR')
    construct_call_graph(dot_dep, tree, True)
    dot_dep.render(filename=output_name + "_dependency")


if __name__ == '__main__':
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument('dir_name', type=str,
                            help='Path to the experiment directory')
    arg_parser.add_argument('--nested', action='store_true',
                            help='Render all directories')

    args = arg_parser.parse_args()
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
