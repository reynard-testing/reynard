
import graphviz


def call_graph(tree: dict):
    pass


def parse_tree(tree: dict):
    pass


def render_call_graph(tree: dict, output_name: str):
    root, nodes = parse_tree(tree)

    use_sig = needs_signature(nodes)
    dot = graphviz.Digraph(comment='Faultspace Search', format='pdf')
    build_tree(dot, root, use_sig, combine)
    dot.render(filename=output_name)
