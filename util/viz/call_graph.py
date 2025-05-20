import argparse
import os
import graphviz
from util import get_json, find_json


def build_call_graph(points: list, implications: list):
    pass


def render_call_graph(data: dict, output_name: str):
    if 'details' not in data or 'implications' not in data:
        print(f"Invalid data structure, skipping rendering {output_name}")
        return
    fault_injection_points = data['details']['fault_injection_points']
    implications = data['implications']

    # dot = graphviz.Digraph(comment='Call graph', format='pdf')
    # build_call_graph(fault_injection_points, implications)
    # dot.render(filename=output_name)


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
