import argparse
import hashlib
import os
from dataclasses import dataclass, field

from graphviz import Digraph
from reynard_viz.points import Call, Point
from reynard_viz.util import find_json, get_json

# This script can be used to render the call graph of a test scenario
# It can be used standalone or as part of a larger visualization pipeline


@dataclass
class Dependency:
    """ Represents a (logical) dependency between calls. """
    dependent: Point
    dependency: list['Point'] = field(default_factory=list)
    inclusion: bool = True


@dataclass(frozen=True)
class CallGraphNode:
    """ Represents a node in the call graph. """
    id: str
    point: Point
    children: list['CallGraphNode'] = field(default_factory=list)
    inclusion_cond: list['Dependency'] = field(default_factory=list)
    exclusion_cond: list['Dependency'] = field(default_factory=list)
    exclusion_relation: list[tuple[str, str]] = field(default_factory=list)
    inclusion_relation: list[tuple[str, str]] = field(default_factory=list)
    causes_fallback: bool = False
    is_fallback: bool = False


def remove_transitive_dependencies(relations: list[tuple[str, str]]) -> list[tuple[str, str]]:
    """ Simplify a relation by removing transitive dependencies."""

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


def get_sanitized_relation(root: Point, deps: list[Dependency]) -> list[tuple[str, str]]:
    """ Sanitize and minify a dependency relation."""
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
    """ Create a consistent dotgraph identifier for a node. """
    base_str = "|".join(
        f"{p.destination}_{p.signature}#{p.count}" for p in node.call_stack)
    safe_str = base_str.replace("/", "_").replace("\n", "")
    hash_str = hashlib.sha256(safe_str.encode()).hexdigest()
    return hash_str


def as_rel_id(id: str) -> str:
    """ The identifier for the relation node in the dot graph. Used to draw arrows between edges. """
    return f"{id}_rel"


def build_tree_from_mapping(node: Point, children_by_parent: dict[Point, list[Call]], dependencies: list[Dependency]) -> CallGraphNode:
    """ Recursively build a node in the call graph """

    # Determine children of the current node
    child_calls = children_by_parent.get(node, [])
    children = [build_tree_from_mapping(
        Point.from_calls(list(node.call_stack) + [x]), children_by_parent, dependencies) for x in child_calls]

    # Determine child dependency relations
    related_dependencies = [
        d for d in dependencies if d.dependent.parent() == node]

    exclusion_cond = [x for x in related_dependencies if not x.inclusion]
    inclusion_cond = [x for x in related_dependencies if x.inclusion]

    min_inclusion_cond = get_sanitized_relation(node, inclusion_cond)
    min_exclusion_cond = get_sanitized_relation(node, exclusion_cond)
    min_exclusion_cond = [
        r for r in min_exclusion_cond if r not in min_inclusion_cond]

    # Determine type of node
    causes_fallback = any(
        x.inclusion and any(
            dep == node for dep in x.dependency
        ) for x in dependencies
    )

    is_fallback = any(
        x.inclusion and x.dependent == node for x in dependencies
    )

    return CallGraphNode(
        id=to_identifier(node),
        point=node,
        children=children,
        exclusion_cond=exclusion_cond,
        inclusion_cond=inclusion_cond,
        exclusion_relation=min_exclusion_cond,
        inclusion_relation=min_inclusion_cond,
        causes_fallback=causes_fallback,
        is_fallback=is_fallback,
    )


def build_call_graph(points: list[Point], dependencies: list[Dependency]) -> tuple[CallGraphNode, list[Point]]:
    # If there is no root point, return None
    if len(points) == 0:
        print("No points found")
        print(points)
        return None, []

    # Sort ports by the length of their call stack
    root = Point.from_calls([points[0].call_stack[0]])

    # Create a mapping of parent points to their children
    # This is to ensure the call grap nodes are precise
    # As the data used can be incomplete in terms of call stack
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


def draw_call_graph(dot: Digraph, node: CallGraphNode, use_dependency: bool = False):
    """ Draw the call graph recursively using the graphviz library. """
    call = node.point.head()

    # Create a dot node for the current call
    dot.node(node.id, label=call.destination)

    # Create a dot node for the relation (between calls)
    node_rel_id = as_rel_id(node.id)
    if node.causes_fallback and use_dependency:
        dot.node(node_rel_id, label="", shape="diamond", fixedsize="true",
                 width="0.25", height="0.25", color="red")
    else:
        dot.node(node_rel_id, label="", shape="point", fixedsize="true",
                 width="0", style="filled", fillcolor="red")

    # Draw an edge from the relation node to the call node
    edge_label = f"{call.signature} #{call.count}"
    edge_color = 'red' if node.is_fallback and use_dependency else 'black'
    dot.edge(node_rel_id, node.id, label=edge_label, color=edge_color)

    # Draw child nodes (recursively)
    for child in node.children:
        draw_call_graph(dot, child, use_dependency)

    # Draw edges to child nodes
    for child in node.children:
        if not child.is_fallback or not use_dependency:
            dot.edge(node.id, as_rel_id(child.id), arrowhead="none")
        else:
            dot.edge(node.id, as_rel_id(child.id),
                     arrowhead="none", style="invis")

    # Draw inclusion and exclusion relations
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
    """ Extract point data from the provided JSON data. """
    points = []
    if 'details' in data and 'fault_injection_points' in data['details']:
        points = data['details']['fault_injection_points']

    pts = []
    for p in points:
        pt = Point.parse(p)
        if pt is not None and pt not in pts:
            pts.append(pt)

    return pts


def get_dependencies_from_data(data: dict) -> list[Dependency]:
    """ Extract dependency data from the provided JSON data. """
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
    # Extract data
    points = get_points_from_data(data)
    dependencies = get_dependencies_from_data(data)

    # Build a call graph
    tree, pts = build_call_graph(points, dependencies)
    if tree is None:
        print("No call graph found")
        return

    # Draw the call graph
    dot = Digraph(comment='Call graph', format='pdf')
    dot.attr(rankdir='LR')
    draw_call_graph(dot, tree, False)
    dot.render(filename=output_name)

    dot_dep = Digraph(comment='Call graph with dependencies', format='pdf')
    dot_dep.attr(rankdir='LR')
    draw_call_graph(dot_dep, tree, True)
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
