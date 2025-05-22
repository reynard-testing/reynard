import os
import argparse
from util import get_json, find_json
from dataclasses import dataclass, replace, field
from tree_viz import parse_tree, combine_tree, SearchNode, get_node_label, id_to_indices
from graphviz import Digraph


@dataclass
class CompareNode:
    id: str
    n1: SearchNode = None
    n2: SearchNode = None
    children: list['CompareNode'] = field(default_factory=list)
    

def match(t1: SearchNode, t2: SearchNode):
    return t1.uid == t2.uid and t1.signature == t2.signature and t1.mode == t2.mode

def build_compare_trees(t1: SearchNode, t2: SearchNode) -> CompareNode:
    base_node = replace(t1)
    children: list[CompareNode] = []

    t2_set = list(t2.children)

    for child1 in t1.children:
        counterpart = None
        for child2 in t2.children:
            if match(child1, child2):
                counterpart = child2
                break
        # only in t1
        if counterpart is None:
            children.append(CompareNode(
                id=child1.id + "-none",
                n1=child1,
                n2=None,
                children=[]
            ))
        else:
            children.append(build_compare_trees(child1, child2))
            t2_set.remove(child2)
    for child2 in t2_set:
        children.append(CompareNode(
            id="none-" + child2.id,
            n1=None,
            n2=child2,
            children=[]
        ))
    

    base_node.children = children
    return CompareNode(
        id= t1.id + "-" + t2.id,
        n1=t1,
        n2=t2,
        children=children,
    )

def get_tree_size(node: SearchNode, shallow=False) -> int:
    size = len(id_to_indices(node.id))
    if shallow:
        return size
    return size + sum([get_tree_size(x) for x in node.children])

def render_cmp_tree(dot: Digraph, node: CompareNode):
    if node.n1 is None:
        size = get_tree_size(node.n2)
        dot.node(node.id, get_node_label(node.n2, True) + f"\n+{size}", color="green")
        return
    if node.n2 is None:
        size = get_tree_size(node.n1)
        dot.node(node.id, get_node_label(node.n1, True) + f"\n-{size}", color="red")
        return
    
    shallow=False
    size_1 = get_tree_size(node.n1, shallow)
    size_2 = get_tree_size(node.n2, shallow)
    if size_1 != size_2:
        ch = "+" if size_2 > size_1 else ""
        dot.node(node.id, get_node_label(node.n1, True) + f"\n{ch}{size_2 - size_1}")
    else:
        dot.node(node.id, "", shape="point")

    for child in node.children:
        dot.edge(node.id, child.id)
        render_cmp_tree(dot, child)


if __name__ == '__main__':
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument('base_dir', type=str,
                            help='Path to the experiment directory')
    arg_parser.add_argument('compare_dir', type=str,
                            help='Path to the experiment directory')
    arg_parser.add_argument('output_dir', type=str,
                            help='Path to the output directory')

    args = arg_parser.parse_args()

    dir1 = args.base_dir
    dir2 = args.compare_dir
    out_dir = args.output_dir

    trees = []
    for directory in [dir1, dir2]:
        data1 = get_json(find_json(directory, "generator"))
        tree, _ = parse_tree(data1['tree'])
        tree = combine_tree(tree)
        trees.append(tree)
    cmp_tree = build_compare_trees(trees[0], trees[1])
    
    dot = Digraph(comment='Faultspace Search', format='pdf')
    render_cmp_tree(dot, cmp_tree)
    base_1 = os.path.basename(dir1)
    base_2 = os.path.basename(dir2)
    print(base_1)
    print(base_2)
    file_name = base_1 + "_" + base_2 + "_cmp"
    dot.render(filename=os.path.join(out_dir, file_name))