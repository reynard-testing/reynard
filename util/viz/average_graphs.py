import argparse
import json
import os

import numpy as np

from graphs import find_json, get_json
from graphs import TIMINGS_OF_INTEREST, TIMINGS_OF_EXTRA_INTEREST
from graphs import render_timing_over_index, render_distribution_of_timing
from graphs import render_queue_size_graph, render_tree


def get_args():
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument('root_dir', type=str,
                            help='Path to the experiment directory')

    args = arg_parser.parse_args()
    return args


def get_dirs(dir: str):
    indexed = [d for d in os.listdir(
        dir) if os.path.isdir(os.path.join(dir, d))]
    indexed.sort(key=lambda x: int(x) if x.isdigit() else x)
    return indexed


def for_dir(directory: str):
    iteration_dirs = [os.path.join(directory, x) for x in get_dirs(directory)]
    print(f"Found {len(iteration_dirs)} iterations in {directory}")

    # skip: not really needed
    # TODO: check if consistent
    ref_generator_data = get_json(find_json(iteration_dirs[0], "generator"))

    for iteration_dir in iteration_dirs[1:]:
        generator_data = get_json(find_json(iteration_dir, "generator"))
        if ref_generator_data != generator_data:
            print(f"Inconsistent generator data in {iteration_dir}!")

    queue_sizes = np.array(ref_generator_data['details']['queue_size'])
    render_queue_size_graph(queue_sizes, directory)
    render_tree(ref_generator_data['tree'],
                os.path.join(directory, 'search_tree'))

    timings_per_key: dict[str, list[float]] = {}
    timings_over_index: dict[str, list[np.array]] = {}

    for iteration_dir in iteration_dirs:
        timing_data = get_json(find_json(iteration_dir, "timing.json"))
        for key in TIMINGS_OF_INTEREST:
            if key not in timing_data['details']:
                continue
            dataset: list = timing_data['details'][key]
            if key not in timings_per_key:
                timings_per_key[key] = []
            timings_per_key[key].extend(dataset)

            if key not in TIMINGS_OF_EXTRA_INTEREST:
                continue
            if key not in timings_over_index:
                timings_over_index[key] = []
            timings_over_index[key].append(np.array(dataset))

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
    print(f"Rendered {directory}")


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


def handle_dir(directory: str):
    subdirs = os.listdir(directory)
    is_multi = "1" in subdirs or any(
        subdir.endswith("-1") for subdir in subdirs)
    if is_multi:
        for_dir(directory)


if __name__ == '__main__':
    args = get_args()
    root_dir = args.root_dir
    handle_dir(root_dir)
    for root, dirs, _ in os.walk(root_dir):
        for dir in dirs:
            handle_dir(os.path.join(root, dir))
