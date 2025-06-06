from graphviz import Digraph
from dataclasses import dataclass, field, replace
import re


@dataclass
class SearchNode:
    id: str
    index: int
    mode: str
    signature: str
    uid: str
    pruned: bool = False
    children: list['SearchNode'] = field(default_factory=list)


def simplify_signature(sig: str) -> str:
    signature = str(sig)
    signature = re.sub(r"\(.+\)", "", signature)
    signature = re.sub(r"(GET|POST|PUT|DELETE) ", "", signature)
    signature = re.sub(r"\{.*\}", "", signature)
    signature_parts = signature.split("/")
    if len(signature_parts) > 1:
        return ".../" + signature_parts[-1]
    return signature


def parse_node(fault: dict):
    mode = fault.get('mode', "")
    uid = fault.get('uid', "")
    uid, signature = simplify_uid(uid)
    return uid, simplify_mode(mode), signature


def simplify_mode(mode: str) -> str:
    v = mode.replace("HTTP_ERROR(", "")
    v = v.replace(")", "")
    return v


def simplify_uid(uid: str):
    signature = ""
    uid_parts = uid.split(">")
    relevant_parts = []
    for i in range(len(uid_parts)):
        p_uid = re.sub(r"\{.*\}", "", uid_parts[i])
        p1, count = p_uid.split("#")
        if ":" in p1:
            destination, signature = p1.split(":")
        else:
            destination = p1
        name = f"{destination}"
        is_relevant = i == len(uid_parts) - 1
        if count != "0":
            is_relevant = True
            name += f"#{count}"

        if is_relevant:
            relevant_parts.append(name)

    uid_str = ">\n".join(relevant_parts)
    return uid_str, simplify_signature(signature)


def simplify_name(name: str):
    if name == "[]":
        return "&empty;", "", ""

    if name.startswith("["):
        names = name[1:-1].split("], Fault[")
        uids = set()
        modes = set()
        signatures = set()
        for n in names:
            uid, mode, signature = simplify_name(n)
            uids.add(uid)
            modes.add(mode)
            signatures.add(signature)
        uid = ",".join(uids)
        mode = ",".join(modes)
        signature = ",".join(signatures)
        return uid, mode, signature

    no_fault = name.replace("Fault", "")
    no_brackets = no_fault.replace("[", "").replace("]", "")
    if no_brackets == "":
        return "&empty;", "", ""

    no_uid = no_brackets.replace("uid=", "")
    if ", mode=" not in no_uid:
        return no_uid, "", ""
    uid, mode = no_uid.split(", mode=")

    uid, signature = simplify_uid(uid)
    return uid, simplify_mode(mode), signature


def get_node_label(node: SearchNode, needs_signature=False):
    parts = [node.uid]
    if needs_signature and node.signature:
        parts.append(node.signature)
    if node.mode:
        parts.append(node.mode)
    return "\n".join(parts)


def id_to_indices(id: str) -> list[int]:
    indices = [int(x) for x in id.split(",")]
    return indices


def get_edge_label(node: SearchNode):
    if not "," in node.id:
        return f""
    parts = [int(x) for x in node.id.split(",") if not '-' in x]
    size = parts[0]

    if size == 0:
        size_pruned = parts[3]
        return f"-{size_pruned}"

    return f"+{size}"


def combine_tree(node: SearchNode):
    # Group children by key
    grouped_by_key: dict[str, list[SearchNode]] = {}
    for child in node.children:
        key = f"{child.uid}\n{child.signature}" if needs_signature else child.uid
        if key not in grouped_by_key:
            grouped_by_key[key] = []
        grouped_by_key[key].append(child)

    new_children: list[SearchNode] = []
    for key, children in grouped_by_key.items():
        ids = [int(c.id) for c in children]
        non_pruned = [x for x in ids if x > -1]
        seq_ids = [0] if len(non_pruned) == 0 else non_pruned
        identifers = [len(non_pruned), min(
            seq_ids), max(seq_ids), len(ids), min(ids), max(ids)]
        combined_id = ",".join(map(str, identifers))

        indices = [c.index for c in children if c.index > -1]
        combined_index = min(indices) if len(indices) > 0 else -1
        combined_mode = ""
        combined_children = []

        for child in children:
            combined_children.extend(child.children)

        combined_child = SearchNode(
            id=combined_id,
            index=combined_index,
            mode=combined_mode,
            signature=children[0].signature,
            uid=children[0].uid,
            children=combined_children,
            pruned=all(c.pruned for c in children)
        )
        new_children.append(combine_tree(combined_child))

    new_node = replace(node)
    new_node.children = sorted(
        new_children, key=lambda c: c.index if c.index > -1 else float('inf'))
    return new_node


def render_tree_structure(dot: Digraph, node: SearchNode, needs_signature=False, with_pruned=False) -> bool:
    if node.pruned and not with_pruned:
        return False
    node_label = get_node_label(node, needs_signature)
    color = 'red' if node.pruned else 'black'
    dot.node(node.id, label=node_label, color=color)

    for child in node.children:
        rendered = render_tree_structure(
            dot, child, needs_signature, with_pruned)
        if not rendered:
            continue
        dot.edge(node.id, child.id, label=get_edge_label(child))
    return True


pruned_counter = 0


def parse_tree(tree: dict) -> tuple[SearchNode, list[SearchNode]]:
    global pruned_counter
    nodes: list[SearchNode] = []

    if isinstance(tree, dict):
        if isinstance(tree.get('node'), list):
            nodes_list = list(map(parse_node, tree['node']))
            # Unpack the results into separate lists for uid, mode, signature
            uids, modes, signatures = zip(
                *nodes_list) if nodes_list else (["&empty;"], [""], [""])
            uid = ",".join(uids)
            mode = ",".join(modes)
            signature = ",".join(signatures)
        else:
            uid, mode, signature = simplify_name(tree['node'])
        children: list[SearchNode] = []
        pruned = tree.get('pruned', True)

        for child in tree.get('children', []):
            child_node, child_nodes = parse_tree(child)
            children.append(child_node)
            nodes.append(child_node)
            nodes.extend(child_nodes)

        index = tree.get('index', -1)
        if pruned:
            pruned_counter += 1
            index = -pruned_counter

        node = SearchNode(
            id=str(index),
            index=index,
            mode=str(mode),
            signature=signature,
            uid=uid,
            pruned=pruned,
            children=children
        )

        nodes.append(node)
        return node, nodes
    else:
        raise ValueError("Invalid tree structure")


def needs_signature(nodes: list[SearchNode]):
    per_uid: dict[str, str] = {}
    for node in nodes:
        if node.uid not in per_uid:
            per_uid[node.uid] = node.signature
        elif per_uid[node.uid] != node.signature:
            return True
    return False


def render_tree(tree: dict, output_name: str, combine=True):
    root, nodes = parse_tree(tree)
    use_sig = needs_signature(nodes)

    if combine:
        root = combine_tree(root)

    dot = Digraph(comment='Faultspace Search', format='pdf')
    render_tree_structure(dot, root, use_sig)
    dot.render(filename=output_name)

    dot = Digraph(comment='Faultspace Search with pruned', format='pdf')
    render_tree_structure(dot, root, use_sig, True)
    dot.render(filename=output_name+"_pruned")
