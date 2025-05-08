from cProfile import label
import json
import argparse
import os
import graphviz


def get_args():
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument('json_path', type=str,
                            help='Path to the JSON file')

    args = arg_parser.parse_args()
    return args


def simplify_name(name: str):
    no_fault = name.lstrip("Fault")
    no_brackets = no_fault.replace("[", "").replace("]", "")
    if no_brackets == "":
        return "[]"

    no_uid = no_brackets.replace("uid=", "")
    uid, mode = no_uid.split(", mode=")

    uid_parts = uid.split(">")
    relevant_parts = []
    for i in range(len(uid_parts)):
        p1, count = uid_parts[i].split("#")
        if ":" in p1:
            dest, sig = p1.split(":")
        else:
            dest = p1
        name = f"{dest}"
        is_relevant = i == len(uid_parts) - 1
        if count != "0":
            is_relevant = True
            name += f"#{count}"

        if is_relevant:
            relevant_parts.append(name)

    uid_str = ">\n".join(relevant_parts)

    mode = mode.replace("HTTP_ERROR(", "")
    mode = mode.replace(")", "")

    return uid_str + "\n" + mode


def build_tree(dot, node, parent=None):
    simpl = simplify_name(node['node'])
    index = node['index']
    dot.node(str(index), label=simpl)

    for child in node.get('children', []):
        build_tree(dot, child, node)
        dot.edge(str(node['index']), str(
            child['index']), label=str(child['index']))


if __name__ == '__main__':
    args = get_args()
    with open(args.json_path, 'r') as file:
        data = json.load(file)
    tree = data['tree']
    dot = graphviz.Digraph(comment='The Round Table', format='svg')
    build_tree(dot, tree)
    directory = os.path.dirname(args.json_path)
    dot.render(directory=directory)
