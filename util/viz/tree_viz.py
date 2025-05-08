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
    no_fault = name.replace("Fault", "")
    no_brackets = no_fault.replace("[", "").replace("]", "")
    if no_brackets == "":
        return "[]", ""

    no_uid = no_brackets.replace("uid=", "")
    if ", mode=" not in no_uid:
        return no_uid, ""
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

    return uid_str, mode


COMBINE = True


def build_tree(dot, node, parent=None):
    uid, mode = simplify_name(node['node'])
    index = node['index']
    dot.node(str(index), label=f"{uid}\n{mode}")

    if COMBINE:
        by_uid = {}
        modes_by_uid = {}
        for child in node.get('children', []):
            child_uid, child_mode = simplify_name(child['node'])
            if child_uid not in by_uid:
                by_uid[child_uid] = []
                modes_by_uid[child_uid] = []
            by_uid[child_uid].append(child)
            modes_by_uid[child_uid].append(child_mode)

        for child_uid, children in by_uid.items():
            if len(children) > 1:
                child_mode = "Multiple"
            else:
                child_mode = modes_by_uid[child_uid][0]
            combined_children = []
            for child in children:
                if 'children' in child:
                    combined_children += child['children']

            combined_index = ",".join([str(c['index']) for c in children])
            min_index = min([c['index'] for c in children])
            combined_index_label = f"{min_index} (+{len(children)})"
            new_child = {
                'node': child_uid,
                'index': combined_index,
                'children': combined_children
            }
            build_tree(dot, new_child, node)
            dot.edge(str(node['index']), combined_index,
                     label=combined_index_label)
    else:
        for child in node.get('children', []):
            build_tree(dot, child, node)
            dot.edge(str(node['index']), str(child['index']),
                     label=str(child['index'] + 1))


if __name__ == '__main__':
    args = get_args()
    with open(args.json_path, 'r') as file:
        data = json.load(file)
    tree = data['tree']
    dot = graphviz.Digraph(comment='The Round Table', format='svg')
    build_tree(dot, tree)
    directory = os.path.dirname(args.json_path)
    dot.render(directory=directory)
