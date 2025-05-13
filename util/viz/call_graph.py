
import graphviz


def build_call_graph(dot, graph: dict):
    pass


def parse_tree(tree: dict):
    pass


def render_call_graph(tree: dict, output_name: str):
    graph, nodes = parse_tree(tree)
    dot = graphviz.Digraph(comment='Faultspace Search', format='pdf')
    build_call_graph(dot, graph)
    dot.render(filename=output_name)
