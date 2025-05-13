import json
import argparse
import os

import numpy as np
import matplotlib.pyplot as plt
from tree_viz import render_tree


def get_args():
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument('json_dir', type=str,
                            help='Path to the experiment directory')
    arg_parser.add_argument('--nested', action='store_true',
                            help='Render all directories')

    args = arg_parser.parse_args()
    return args


def get_json(file_path):
    with open(file_path, 'r') as file:
        return json.load(file)


def find_json(dir: str, s: str):
    for root, _, files in os.walk(dir):
        for file in files:
            if file.endswith(".json") and s.lower() in file.lower():
                return os.path.join(root, file)
    return None


def render_queue_size_graph(queue_sizes, output_dir):
    # Plot the data
    plt.figure(figsize=(10, 6))
    plt.plot(queue_sizes, linestyle='-', label='Queue Size')

    # Add labels and title
    plt.xlabel('Index')
    plt.ylabel('Queue Size')
    plt.title('Queue Size vs Index')
    plt.legend()

    # Save the plot to a file
    output_file = os.path.join(output_dir, 'queue_size_plot.svg')
    plt.savefig(output_file)
    plt.close()


def render_distribution_of_timing(timings: list[tuple[str, list[float]]], output_dir: str):
    # Create a figure with subplots
    num_timings = len(timings)

    fig, axes = plt.subplots(1, num_timings, figsize=(
        5 * num_timings, 6), squeeze=False)

    for i, (key, data) in enumerate(timings):
        data_ms = np.array(data) * 1e-6  # Convert to milliseconds

        # Boxplot
        axes[0, i].boxplot(data_ms, vert=True, patch_artist=True)
        axes[0, i].set_title(f'{key} (Boxplot)')
        axes[0, i].set_ylabel('Time (ms)')

        # Violin plot
        violin_ax = axes[0, i].twinx()
        violin_ax.violinplot(data_ms, showmeans=True, showextrema=True)
        violin_ax.set_ylabel('Time (ms)')

    # Save the plot to a file
    output_file = os.path.join(output_dir, 'timing_distribution_plot.svg')
    plt.savefig(output_file)
    plt.close()


def render_timing_over_time(key, data, output_dir):
    # Plot the data
    plt.figure(figsize=(10, 6))
    data_ms = np.array(data) * 1e-6  # Convert to milliseconds
    plt.plot(data_ms, linestyle='-', label=key)

    # Add labels and title
    plt.xlabel('Index')
    plt.ylabel('Time (ms)')
    # plt.title('Timing Over Time')
    plt.legend()

    # Save the plot to a file
    output_file = os.path.join(output_dir, f'{key}_over_time_plot.svg')
    plt.savefig(output_file)
    plt.close()


TIMINGS_OF_INTEREST = ['nextFaultload', 'Per test', 'registerFaultload',
                       'unregisterFautload', 'testMethod', 'getTraceWithDelay', 'handleResult']
TIMINGS_OF_EXTRA_INTEREST = ['nextFaultload',
                             'handleResult', 'registerFaultload', 'Per test']


def render_for_dir(json_dir: str):
    generator_data = get_json(find_json(json_dir, "generator"))

    queue_sizes = generator_data['details']['queue_size']
    queue_sizes = np.array(queue_sizes)
    render_queue_size_graph(queue_sizes, json_dir)
    render_tree(generator_data['tree'], os.path.join(json_dir, 'search_tree'))

    timing_data = get_json(os.path.join(json_dir, 'timing.json'))
    timings: tuple[str, list[float]] = []
    for key in TIMINGS_OF_INTEREST:
        if key not in timing_data['details']:
            continue
        dataset = np.array(timing_data['details'][key])
        timings.append((key, dataset))
        if key in TIMINGS_OF_EXTRA_INTEREST:
            render_timing_over_time(key, dataset, json_dir)
    render_distribution_of_timing(timings, json_dir)
    print(f"Rendered {json_dir}")


if __name__ == '__main__':
    args = get_args()
    json_dir = args.json_dir
    if args.nested:
        for root, dirs, _ in os.walk(json_dir):
            for dir in dirs:
                render_for_dir(os.path.join(root, dir))
    else:
        render_for_dir(json_dir)
