import argparse
import json
import os
from unittest import loader

import numpy as np

from util import get_json, find_json
from graphs import TIMINGS_OF_INTEREST, TIMINGS_OF_INTEREST_OVER_TIME
from graphs import render_timing_over_index, render_distribution_of_timing
from graphs import render_queue_size_graph, render_tree
from call_graph import render_call_graph
import config


class DataLoader:
    def get_sub_dirs(self):
        sub_dirs = [os.path.join(self.root_dir, d) for d in os.listdir(
            self.root_dir) if os.path.isdir(os.path.join(self.root_dir, d))]
        return sub_dirs

    def __init__(self, root_dir: str):
        self.root_dir = root_dir
        self.sub_dirs = self.get_sub_dirs()
        self.generator_data = []
        self.timing_data = []
        self.search_space_data = []
        self.pruner_data = []

        self.reference_generator_data = None
        self.reference_search_space = None
        self.reference_pruner_data = None

    def load(self):
        for sub_dir in self.sub_dirs:
            generator_json = find_json(sub_dir, "generator")
            if generator_json is not None:
                self.generator_data.append(get_json(generator_json))

            timing_json = find_json(sub_dir, "timing.json")
            if timing_json is not None:
                self.timing_data.append(get_json(timing_json))

            search_json = find_json(sub_dir, "search_space.json")
            if search_json is not None:
                self.search_space_data.append(get_json(search_json))

            pruners_json = find_json(sub_dir, "pruners.json")
            if pruners_json is not None:
                self.pruner_data.append(get_json(pruners_json))

        self.reference_generator_data = self.generator_data[0]
        self.generator_data = self.generator_data[1:]

        self.reference_search_space = self.search_space_data[0]
        self.search_space_data = self.search_space_data[1:]

        self.reference_pruner_data = self.pruner_data[0]
        self.pruner_data = self.pruner_data[1:]
        print(
            f"Loaded {len(self.generator_data) + 1} entries from {self.root_dir}")


def get_args():
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument('root_dir', type=str,
                            help='Path to the experiment directory')
    arg_parser.add_argument('--all', action='store_true')
    arg_parser.add_argument('--format', type=str,
                            default='svg', choices=['svg', 'png'],
                            help='Output format for the graphs')

    args = arg_parser.parse_args()
    return args


def determine_inconsistencies(loader: DataLoader) -> tuple[int, set]:
    generator_data = loader.reference_generator_data
    inconcistencies_found = 0
    errors = set()
    for other_generator_data in loader.generator_data:
        is_inconsistent = False
        if generator_data['stats'] != other_generator_data['stats']:
            is_inconsistent = True
            errors.add('gen-stats')
        if generator_data['details'] != other_generator_data['details']:
            is_inconsistent = True
            errors.add('gen-stats')
        if generator_data['tree'] != other_generator_data['tree']:
            is_inconsistent = True
            errors.add('gen-tree')
        if is_inconsistent:
            inconcistencies_found += 1

    pruner_data = loader.reference_pruner_data
    for other_pruner_data in loader.pruner_data:
        if not 'DynamicReductionPruner' in pruner_data:
            continue
        if pruner_data['DynamicReductionPruner'] != other_pruner_data['DynamicReductionPruner']:
            errors.add('pruner-dynamic-reduction')

    search_space_data = loader.reference_search_space
    for other_search_space_data in loader.search_space_data:
        if search_space_data != other_search_space_data:
            errors.add('search-space')

    return inconcistencies_found, errors


def for_dir(directory: str):
    loader = DataLoader(directory)
    loader.load()

    inconcistencies_found, inconsitency_errors = determine_inconsistencies(
        loader)
    if inconcistencies_found > 0:
        print(f"{inconcistencies_found} inconcistencies in data in {directory}!")
        print(f"errors: {inconsitency_errors}")

    generator_data = loader.reference_generator_data
    queue_sizes = np.array(generator_data['details']['queue_size'])
    render_queue_size_graph(queue_sizes, directory)
    render_tree(generator_data['tree'],
                os.path.join(directory, 'search_tree'))
    render_call_graph(generator_data,
                      os.path.join(directory, 'call_graph'))
    render_statistics(loader, directory, True)
    print(f"Rendered {directory}")


def render_statistics(loader: DataLoader, directory: str, over_time: bool = False):
    timings_per_key: dict[str, list[float]] = {}
    timings_over_index: dict[str, list[np.array]] = {}

    for timing_data in loader.timing_data:
        for key in TIMINGS_OF_INTEREST:
            if key not in timing_data['details']:
                continue
            dataset: list = timing_data['details'][key]
            if key not in timings_per_key:
                timings_per_key[key] = []
            timings_per_key[key].extend(dataset)

            if not over_time or key not in TIMINGS_OF_INTEREST_OVER_TIME:
                continue
            if key not in timings_over_index:
                timings_over_index[key] = []
            timings_over_index[key].append(np.array(dataset))

    if over_time:
        avg_timings_over_index = {key: np.mean(np.array(data), axis=0)
                                  for key, data in timings_over_index.items()}

        # average per index over all iterations
        for key in avg_timings_over_index:
            render_timing_over_index(
                key, avg_timings_over_index[key], directory)

    timings: list[tuple[str, list[float]]] = [
        (key, np.array(data)) for key, data in timings_per_key.items()]
    # average over all iterations
    render_distribution_of_timing(timings, directory)
    output_timing_stats(timings_per_key, directory)

    test_timer_per_iteration = []
    for timing_data in loader.timing_data:
        if 'Total test time' in timing_data['details']:
            avg_time = timing_data['stats']['Total test time']['average']['ns']
            test_timer_per_iteration.append(avg_time)

    render_timing_over_index(
        'test_time_per_it', test_timer_per_iteration, directory)

    # Output results summary
    pruner_data = loader.reference_pruner_data
    search_space_data = loader.reference_search_space
    # Convert to seconds
    test_time_data = np.array(
        timings_per_key.get("Total test time", [])) * 1e-9
    dynamic_reduction = 0
    if 'DynamicReductionPruner' in pruner_data:
        dynamic_reduction = pruner_data['DynamicReductionPruner']['directly_pruned']['count']
    output_results({
        "explored": search_space_data['total_run'],
        "dynamic_reduction": dynamic_reduction,
        "time_avg_s": np.mean(test_time_data),
        "time_avg_n": len(test_time_data),
    }, directory)


def output_results(output: dict, directory: str):
    data = output

    results_path = os.path.join(directory, "results.json")
    with open(results_path, "w") as f:
        json.dump(data, f, indent=4)


def output_timing_stats(timings_per_key, directory: str):
    data = {
        "per_key": {},
    }

    for key in timings_per_key:
        # Convert to milliseconds
        dataset = np.array(timings_per_key[key]) * 1e-6
        data["per_key"][key] = {
            "n": len(dataset),
            "mean": np.mean(dataset),
            "median": np.median(dataset),
            "std": np.std(dataset),
            "min": np.min(dataset),
            "max": np.max(dataset),
        }
        for percentiles in [25, 50, 75, 90, 95, 99]:
            percentile = np.percentile(dataset, percentiles)
            data["per_key"][key][f"p{percentiles}"] = percentile

    stats_path = os.path.join(directory, "stats.json")
    with open(stats_path, "w") as f:
        json.dump(data, f, indent=4)


def is_dir_of_interest(directory: str):
    subdirs = os.listdir(directory)
    is_multi = "1" in subdirs or any(
        subdir.endswith("-1") for subdir in subdirs)
    return is_multi


def handle_dir(directory: str):
    if is_dir_of_interest(directory):
        for_dir(directory)


if __name__ == '__main__':
    args = get_args()
    root_dir = args.root_dir
    config.OUTPUT_FORMAT = args.format

    if args.all:
        dirs_of_interest = []
        for root, dirs, _ in os.walk(root_dir):
            if is_dir_of_interest(root):
                print(f"Found {root}")
                dirs_of_interest.extend([os.path.join(root, x)
                                        for x in get_dirs(root)])
        print(f"Found {len(dirs_of_interest)} iterations in {root_dir}")
        render_statistics(dirs_of_interest, root_dir)
        print(f"Rendered {root_dir}")
    else:
        handle_dir(root_dir)
        for root, dirs, _ in os.walk(root_dir):
            for dir in dirs:
                handle_dir(os.path.join(root, dir))
