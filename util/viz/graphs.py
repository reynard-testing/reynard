import argparse
import os

import numpy as np
import matplotlib.pyplot as plt
from tree_viz import render_tree
from call_graph import render_call_graph
from util import get_json, find_json
import config


def get_args():
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument('json_dir', type=str,
                            help='Path to the experiment directory')
    arg_parser.add_argument('--nested', action='store_true',
                            help='Render all directories')

    args = arg_parser.parse_args()
    return args


def render_queue_size_graph(queue_sizes, output_dir):
    # Plot the data
    plt.figure(figsize=(10, 6))
    plt.plot(queue_sizes, linestyle='-', label='Queue Size')

    # Add labels and title
    plt.xlabel('Index')
    plt.ylabel('Queue Size')
    ax = plt.gca()
    ax.yaxis.get_major_locator().set_params(integer=True)
    ax.xaxis.get_major_locator().set_params(integer=True)
    plt.title('Queue size over time')
    plt.legend()

    # Save the plot to a file
    output_file = os.path.join(
        output_dir, 'queue_size_plot.' + config.OUTPUT_FORMAT)
    plt.savefig(output_file, dpi=config.OUTPUT_DPI)
    plt.close()


def render_distribution_of_timing(timings: list[tuple[str, list[float]]], output_dir: str):
    # Create a figure with subplots
    for key, data in timings:
        data_ms = np.array(data) * 1e-6  # Convert to milliseconds

        plt.figure(figsize=(8, 6))
        plt.violinplot(data_ms, showmeans=True, showextrema=True)
        plt.boxplot(data_ms, vert=True, patch_artist=True,
                    widths=0.2, positions=[1])

        title = key
        if key in PLOT_NAMES_BY_KEY:
            title = PLOT_NAMES_BY_KEY[key]
        plt.title(title)
        plt.ylabel('Time (ms)')
        plt.ylim(bottom=0)
        combined_file = os.path.join(
            output_dir, f'{key}_violin_boxplot.{config.OUTPUT_FORMAT}')
        plt.savefig(combined_file, dpi=config.OUTPUT_DPI)
        plt.close()


def render_timing_over_index(key, data, output_dir):
    # Plot the data
    plt.figure(figsize=(10, 6))
    data_ms = np.array(data) * 1e-6  # Convert to milliseconds
    plt.plot(data_ms, linestyle='-', label=key)

    # Add labels and title
    plt.xlabel('Index')
    plt.ylabel('Average time (ms)')

    ax = plt.gca()
    ax.xaxis.get_major_locator().set_params(integer=True)
    ax.set_ylim(bottom=0)
    plt.title('Average time per index')
    plt.legend()

    # Save the plot to a file
    output_file = os.path.join(
        output_dir, f'{key}_over_time_plot.' + config.OUTPUT_FORMAT)
    plt.savefig(output_file, dpi=config.OUTPUT_DPI)
    plt.close()


TIMINGS_OF_INTEREST = ['nextFaultload', 'Per test', 'registerFaultload',
                       # , 'DynamicReductionPruner.prune<Pruner>'
                       'unregisterFautload', 'testMethod', 'getTraceWithDelay', 'handleResult', 'Total test time', 'getExpectedBehaviours', 'getExpectedPoints'
                       ]
TIMINGS_OF_INTEREST_OVER_TIME = ['nextFaultload',
                                 'handleResult', 'registerFaultload', 'Per test', 'DynamicReductionPruner.prune<Pruner>', 'getExpectedBehaviours', 'getExpectedPoints']

PLOT_NAMES_BY_KEY = {
    'nextFaultload': 'Next Faultload',
    'Per test': 'Per Test',
    'registerFaultload': 'Register Faultload',
    'unregisterFautload': 'Unregister Faultload',
    'testMethod': 'Test Method',
    'getTraceWithDelay': 'Get Trace With Delay',
    'handleResult': 'Handle Result',
    'Total test time': 'Total Test Time',
    'DynamicReductionPruner.prune<Pruner>': 'Dynamic Reduction Pruner',
}


def render_for_dir(json_dir: str):
    generator_data = get_json(find_json(json_dir, "generator"))
    queue_sizes = np.array(generator_data['details']['queue_size'])
    render_queue_size_graph(queue_sizes, json_dir)
    render_tree(generator_data['tree'], os.path.join(json_dir, 'search_tree'))
    render_call_graph(generator_data, os.path.join(json_dir, 'call_graph'))

    timing_data = get_json(os.path.join(json_dir, 'timing.json'))
    timings: tuple[str, list[float]] = []

    for key in TIMINGS_OF_INTEREST:
        if key not in timing_data['details']:
            continue
        dataset = np.array(timing_data['details'][key])
        timings.append((key, dataset))
        if key in TIMINGS_OF_INTEREST_OVER_TIME:
            render_timing_over_index(key, dataset, json_dir)
    render_distribution_of_timing(timings, json_dir)
    print(f"Rendered {json_dir}")


if __name__ == '__main__':
    args = get_args()
    json_dir = args.json_dir
    if args.nested:
        for root, dirs, filenames in os.walk(json_dir):
            dirname = os.path.basename(root)
            if dirname != 'default' or 'timing.json' not in filenames:
                continue
            render_for_dir(root)

    else:
        render_for_dir(json_dir)
