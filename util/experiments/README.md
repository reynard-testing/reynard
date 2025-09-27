# Experiments

This directory contains scripts to ease the process iteratively running Reynard in different scenarios for specific benchmarks.

### General notes

- Be carefull in the relative location and naming of checked out repositories. Most scripts expect dependent projects to be present at a specific path relative to their own.
- The file [junit-platform.properties](/lib/src/test/resources/junit-platform.properties) defines _where_ the Reynard results are put.

### Requirements

- All experiments require an installation of Docker and Docker compose.
- All script require bash. Use either a linux distro or bash for windows.

## Comparison with Filibuster

We can compare Reynard with Filibuster using the Filibuster Corpus; as set of microbenchmarks that correspond to common system interactions in microservices.

### Re-running Filibuster on the Corpus

#### Setup

For a fair comparison, we must re-run Filibuster with the same failure modes as Reynard.
Furthermore, we need to tweak Filibuster slightly to ensure it runs stable in 2025.
For this, we have created a fork of Filibuster with the [changes required](https://github.com/delanoflipse/filibuster-comparison/pull/1) to run it.
You can find the [changed version here](https://github.com/delanoflipse/filibuster-comparison/tree/track-changes) (keep in mind it uses a branch).

We have to use a similar process to run the corpus (again). These changes are [tracked here](https://github.com/delanoflipse/filibuster-corpus/pull/3).

To simplify running all experiments, we introduce a script called `run_experiments_n.sh` that will automatically run all used microbenchmarks (inluding building them, starting and stopping) for a configurable amount of iterations.

#### Experiments

In short, to run the whole suite, run:

```sh
cd path/to/some/dir

# Clone patched Filibuster into ./filibuster
git clone https://github.com/delanoflipse/filibuster-comparison.git filibuster
cd filibuster; git checkout track-changes; git pull

# Clone patched Corpus into ./corpus
git clone https://github.com/delanoflipse/filibuster-corpus.git corpus
cd corpus; git checkout baseline; git pull

# Run Experiments
cd filibuster
N=10 ./run_experiments_n.sh <optional tag>
```

#### Post-processing

As a result, this will create a dump of logs from the Filibuster runs in the format `results/<benchmark>/<run-id>/filibuster.log`.
At the bottom of this log is a summary of the results (cases and runtime). To avoid having to manually extract these values, we include a small extract script that extracts these values. These are found [here](./filibuster/extract/).

### Running Reynard on the Corpus

#### Setup

To run Reynard on the corpus, we have to tweak the corpus to enable our instrumentation.
The required setup and branch [is found here](https://github.com/delanoflipse/filibuster-corpus/pull/5).
The scripts in this repository expect this file structure:

```sh
|- projects/reynard # (this repository)
|- benchmarks/filibuster # (the adjusted corpus)
```

Most scripts accept setting a `CORPUS_PATH` environment variable if needed.

#### Experiments

We can then use the benchmarks in [FilibusterSuiteIT](/lib/src/test/java/dev/reynard/junit/integration/FilibusterSuiteIT.java).
For ease of use (by automatically building, starting and stopping the benchmarks), we can use [the provided scripts](./filibuster/).

```sh
cd path/to/reynard/installation

# Clone patched Corpus into ./corpus
cd ../../
mkdir benchmarks
git clone https://github.com/delanoflipse/filibuster-corpus.git filibuster
cd filibuster; git checkout ds-fit-benchmark-changes; git pull

# Run Experiments
cd path/to/reynard/installation
N=10 ./util/experiments/filibuster/run_all_filibuster_n.sh <optional tag>
```

Tip: for debugging purposes, you can also follow the steps to start a microbenchmark and then debug a corresponding test suite in your IDE.

#### Post-processing

This will result in a number of logs placed into `results/tests/<benchmark>/<run-id>/..`.
At the end of this file is a description how these can be further processed.

## Other benchmark systems

## Overhead Benchmark

# Post-processing

To process the raw data for both visualisation and analytics, we provide a number of post-processing scripts in `util/viz`.
A description can be found [here](../viz/README.md).
