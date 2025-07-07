# Visualisation tools for Reynard

This directory contains python scripts to generate various figures from Reynard output data.

## Requirements

- [Poetry](https://python-poetry.org/docs/#installation) (package manager)
- Python 3.10+

## Installation

```bash
cd <path-to-reynard>/util/viz
poetry install
```

## Usage

To run commands, use either `poetry run python <script>` or use `poetry shell` and then `python <script>`.

### Combined viz

This script is intended to visualize a directory of results.
It will recursively look for directories of interest and output all types of graphs.
You can use the `--all` flag to get combined results for all subdirectories (for analytical purpoposes).

```bash
python viz_all.py <relative-path-to-result-dir> [--all] [--format=svg|png]
```

Outputs:

- Call graphs
- Search Trees
- Average timing for certain components
- Summary JSON

Some of these can be rendered stand-alone (see scripts).

### Search Tree Comparison

Compare two search trees for analytical purposes.

```bash
python compare_tree.py <rel-dir-1> <rel-dir-2> <rel-output-dir>
```
