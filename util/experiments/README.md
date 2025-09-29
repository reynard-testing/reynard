# Experiments

This directory contains scripts to ease the process iteratively running Reynard in different scenarios for specific benchmarks.

### General notes

- Be carefull in the relative location and naming of checked out repositories. Most scripts expect dependent projects to be present at a specific path relative to their own.
- The file [junit-platform.properties](/lib/src/test/resources/junit-platform.properties) defines _where_ the Reynard results are put.

### Requirements

- All experiments require an installation of Docker and Docker compose.
- All script require bash. Use either a linux distro or bash for windows.
- Some script use [poetry](https://python-poetry.org/) as a python package manager.

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
cd filibuster; git checkout track-changes; git pull; cd ../

# Clone patched Corpus into ./corpus
git clone https://github.com/delanoflipse/filibuster-corpus.git corpus
cd corpus; git checkout baseline; git pull; cd ../

# Run Experiments
cd filibuster
poetry install
N=10 ./run_experiments_n.sh <optional tag>
DISABLE_DR=1 N=10 ./run_experiments_n.sh <tag> # For ablation
```

#### Post-processing

As a result, this will create a dump of logs from the Filibuster runs in the format `results/<benchmark>/<run-id>/filibuster.log`.
At the bottom of this log is a summary of the results (cases and runtime). To avoid having to manually extract these values, we include a small extract script that extracts these values. These are found [here](./filibuster/extract/).

### Running Reynard on the Corpus

#### Setup

To run Reynard on the corpus, we have to tweak the corpus to enable our instrumentation.
The required setup and branch [is found here](https://github.com/delanoflipse/filibuster-corpus/pull/7).
The scripts in this repository expect this file structure:

```sh
|- reynard # (this repository)
|- benchmarks/filibuster # (the adjusted corpus)
```

Most scripts accept setting a `CORPUS_PATH` environment variable if needed.

#### Experiments

We can then use the benchmarks in [FilibusterSuiteIT](/lib/src/test/java/dev/reynard/junit/integration/FilibusterSuiteIT.java).
For ease of use (by automatically building, starting and stopping the benchmarks), we can use [the provided scripts](./filibuster/).

```sh
cd path/to/some/dir

# Clone Reynard
git clone https://github.com/reynard-testing/reynard.git reynard

# Setup reynard
make build-all
make install

# Clone patched Corpus
mkdir benchmarks
git clone https://github.com/delanoflipse/filibuster-corpus.git benchmarks/filibuster-corpus
cd benchmarks/filibuster-corpus; git checkout reynard-changes; git pull; cd ../../

# Run Experiments
cd reynard
N=10 ./util/experiments/filibuster/run_all_filibuster_n.sh <optional tag>
USER_SER=false N=10 ./run_experiments_n.sh <tag> # For ablation
```

Tip: for debugging purposes, you can also follow the steps to start a microbenchmark and then debug a corresponding test suite in your IDE.

#### Post-processing

This will result in a number of logs placed into `results/tests/<benchmark>/<run-id>/..`.
If you ran the results with different tags, put them into seperate directories, as the post-processing expects all subfolders to be for seperate runs of the same test scenario.
At the end of this file is a description how these can be further processed.

## Other benchmark systems

The process for the other benchmarks is similar to the Filibuster Corpus.
We expect a same directory structure with a neighbouring benchmark directory to reynard.

### Astronomy shop

```sh
# Clone Astronomy shop
mkdir benchmarks
git clone https://github.com/delanoflipse/opentelemetry-demo-ds-fit.git benchmarks/astronomy-shop
cd benchmarks/astronomy-shop; git checkout track-changes; git pull; cd ../../

# Run Experiments
cd reynard
N=10 ./util/experiments/otel/run_all_otel.sh <optional tag>
USER_SER=false N=10 ./util/experiments/otel/run_all_otel.sh <tag> # For ablation
```

### DeathStarBench

```sh
# Clone DeathStarBench
mkdir benchmarks
git clone https://github.com/delanoflipse/DeathStarBench.git benchmarks/DeathStarBench
cd benchmarks/DeathStarBench; git checkout fit-instrumentation; git pull; cd ../../

# Run Experiments
cd reynard
N=10 ./util/experiments/hotelreservation/run_all_n.sh <optional tag>
USER_SER=false N=10 ./util/experiments/hotelreservation/run_all_n.sh <tag> # For ablation
```

### "Meta" (Reynard) and Micro Benchmarks

```sh
cd path/to/reynard

# Meta
PROXY_RETRY_COUNT=2 N=10 ./util/experiments/meta/run_all_meta.sh <optional tag>
USER_SER=false N=10 ./util/experiments/meta/run_all_meta.sh <tag> # For ablation

# Micro
N=10 ./util/experiments/meta/run_all_meta.sh <optional tag>
USER_SER=false N=10 ./util/experiments/meta/run_all_meta.sh <tag> # For ablation
```

# Post-processing

To process the raw data for both visualisation and analytics of a Reynard run, we provide a number of post-processing scripts in `util/viz`.
A description can be found [here](../viz/README.md).

# Artifacts

The [artifacts repository](https://github.com/reynard-testing/experiment-artifacts) contains the raw logs used in the results, as well as relevant post-processing results.
For ease of finding files, we've renamed some files or extracted them outside their folders.
For example, the overhead are renamed to the single files in order of the experiments.

# Overhead Benchmark

A detailed description of the overhead benchmarks can be found [in its directory](./overhead/).

To run:

```sh
cd path/to/reynard
./util/experiments/overhead/service_overhead.sh

# Or run a single test
TEST=<test-name> ./util/experiments/overhead/service_overhead.sh
```

This logs the output of wrk in `results/overhead/<scenario>/wrk.log` files, which tracks all related results.
