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

This script is intended to visualize a directory of **runs** (not logs) in the results directory.
It will search recursively for folders of interest, so you can also provide it with a directory containing directories with runs.
You can use the `--all` flag to get _combined_ results for all subdirectories (for analytical purpoposes).

```bash
python viz_all.py <relative-path-to-results-runs-dir> [--all] [--format=svg|png]
```

Outputs:

- Call graphs
- Search Trees
- Average timing for certain components
- Summary JSON

Some of these can be rendered stand-alone (see scripts).

### Search Tree Comparison

Compare two search trees for analytical purposes.
The input is:

- Two directories that correspond to a single execution. These are the folders directly containing the `.json` files corresponding to a single execution of a Reynard test case, i.e. `"./results/runs/<benchmark>/default/<suite>#<test>/default-1"`
- An output directory

```bash
python compare_tree.py <path-to-json-dir-1> <path-to-json-dir-2> <path-to-output-dir>
```
