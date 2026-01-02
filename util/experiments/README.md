# Reynard Experimentation

This directory contains everything to run Reynard in different scenarios for specific benchmarks.
This includes scripts to simplify the experimentation process, as well as this description.

### General notes

- Be careful in the relative location and naming of checked out repositories. Most scripts expect dependent projects to be present at a _specific_ path relative to their own.
- The file [junit-platform.properties](/library/src/test/resources/junit-platform.properties) defines _where_ the Reynard results are put. Alternatively, you can run reynard with a `OUTPUT_DIR` environment variable set.

## Requirements

### Software Requirements

- All experiments require an installation of `Docker` and `Docker Compose`.
- Reynard requires `Java JDK 17` and `Maven`.
- All script require `bash`. Use a linux distribution, or bash for windows.
- Our patched version of Filibuster uses [poetry](https://python-poetry.org/) as a python package manager. Some post-processing scripts in this repository use poetry too.
- The experiments use the local images of the proxy and controller (so they are up-to-date). Run `make build-all` in the project root to generate them.

### Hardware Requirements

As the benchmarks require running a (benchmark) microservice application on a single machine, there are hardware requirements, especially for the Astronomy Shop benchmark. We were able to reproduce the experiments on the following hardware specs:

| CPU                                  | Memory    | Used in results: |
| ------------------------------------ | --------- | ---------------- |
| AMD Ryzen 7 3700X (8 cores @3.6 GHz) | 32GB DDR4 | ✔               |
| Intel i7-6700HQ (4 cores @3.4 GHz)   | 16GB DDR4 |                  |

## Artifacts

If you are only interested in the raw data used to generate the results, please look at the [artifacts repository](https://github.com/reynard-testing/experiment-artifacts).
It contains the raw logs used in the results, as well as relevant post-processing results.
Note that, for ease of finding files, we've renamed or moved a small number of files.

## Post-processing

To process the raw data for both visualisation and analytics of a Reynard run, we provide a number of post-processing scripts in `util/viz`.
A description can be found [here](../viz/README.md).

# Experiments

We have three experiments that we use in the results, which we detail in the following sections:

1. Running Reynard on different benchmark systems (including the filibuster corpus)
2. Running Filibuster on its corpus with a configuration that matches Reynard
3. A stress-test on the Reynard instrumentation

## 1. Running Reynard on different benchmarks

This experiment requires both reynard to be build, as well as the numerous benchmark systems it should run on. These benchmark systems are:

1. [Filibuster Corpus](https://github.com/filibuster-testing/filibuster-corpus)' set of microbenchmarks
1. [DeathStarBench](https://github.com/delimitrou/DeathStarBench)'s hotelReservation
1. OpenTelemetry's [Astronomy shop](https://github.com/open-telemetry/opentelemetry-demo)
1. Microbenchmarks included in Reynard
1. Reynard itself (we refer to these tests as "meta" tests)

### 1.1. Installation

#### 1.1.1. Cloning repositories

The experiment scripts in this repository expect this file structure:

```sh
|- reynard # (this repository)
|- benchmarks/ # (the different benchmark systems)
```

To run Reynard on the the benchmark systems, we have to tweak them to enable our instrumentation.
To get all required repositories, run the following [script](./setup/01_clone.sh) in an **empty** directory:

```bash
cd <emtpy experimentation directory>

# Clone Reynard repository
git clone --single-branch https://github.com/reynard-testing/reynard.git reynard

# Clone benchmarks
git clone -b reynard-changes --single-branch https://github.com/delanoflipse/filibuster-corpus.git benchmarks/filibuster-corpus
git clone -b track-changes --single-branch  https://github.com/delanoflipse/opentelemetry-demo-ds-fit.git benchmarks/astronomy-shop
git clone -b fit-instrumentation --single-branch https://github.com/delanoflipse/DeathStarBench.git benchmarks/DeathStarBench
```

#### 1.1.2. Building Docker images

All benchmarks are large docker-based microservice applicatons.
Furthermore, we want to build the last version of the reynard instrumentation.
To build _all_ required docker images, run the following [script](./setup/02_clone.sh) in an empty directory:

```bash
cd <experimentation directory>

# Build Reynard images
cd ./reynard; make build-all

# Most docker compose files use these environment variables, so provide them
export PROXY_IMAGE=${PROXY_IMAGE}
export CONTROLLER_IMAGE=${CONTROLLER_IMAGE}

# Astronomy shop
cd ./benchmarks/astronomy-shop; (docker compose -f docker-compose.fit.yml build)

# DeathStarBench hotelReservation
cd ./benchmarks/DeathStarBench/hotelReservation; (docker compose -f docker-compose.fit.yml build)

# Filibuster corpus
cd ./benchmarks/filibuster-corpus/cinema-1; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-2; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-3; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-4; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-5; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-6; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-7; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-8; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/audible; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/netflix; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/expedia; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/mailchimp; (docker compose -f docker-compose.fit.yml build)
```

Note: This will take **a long time** to complete.
If you are only interested in a particular benchmark, you can omit the corresponding build steps.

#### 1.1.3. Building Reynard (optional)

We offer a dockerised runtime for reynard.
However, for our results we ran the experiments locally to minimise any potential overhead.
For this, you need Java JDK 17, maven, and you need to install Reynard using the following command:

```sh
cd <experimentation directory>/reynard
make install # Build and install library
```

#### 1.1.4. Running all experiments at once (optional)

If you want to regenerate _all_ experimental results, you can use the following [script](./setup/03_run.sh).
Below, we will detail each individual benchmark.

### 1.2. Post-processing

Each test suite will result in several logs placed into `<results-dir>/<benchmark>/<run-id>/..`.
The post-processing scripts expect all subfolders to be for separate runs of the same test scenario.
Hence, if you used tags, please move the related runs into a separate folder.

After all benchmarks have been run, you can use the `util/viz` scripts to post-process the results, as [described here](/util/viz/).

### 1.3 Filibuster Corpus

We combined all Reynard Filibuster Corpus experiments in the [FilibusterSuiteIT](/library/src/test/java/dev/reynard/junit/integration/FilibusterSuiteIT.java) test suite.

We can use [the provided scripts](./filibuster/) to automatically build, start, and stop each benchmark.
To run the whole suite, run:

```sh
cd <experimentation directory>/reynard
export OUT_DIR=<absolute-path>

# Uncomment if you intent to use your local maven installation
# export USE_DOCKER=false

N=10 ./util/experiments/filibuster/run_all_n.sh

# Run experiments once without SER for ablation
TAG="NO-SER" USE_SER=false N=1 ./util/experiments/filibuster/run_all_n.sh
```

Tip: if you want to debug an execution, you can follow the steps to start one of the benchmarks and then debug the corresponding test method in your IDE if you use reynard locally.

### 1.4. Astronomy shop

```sh
cd <experimentation directory>/reynard

# Uncomment if you intent to use your local maven installation
# export USE_DOCKER=false

# Run Experiments
N=10 ./util/experiments/otel/run_all_n.sh

# Run experiments once without SER for ablation
TAG="NO-SER" USE_SER=false N=1 ./util/experiments/otel/run_all_n.sh
```

### 1.5. DeathStarBench

```sh
cd <experimentation directory>/reynard

# Uncomment if you intent to use your local maven installation
# export USE_DOCKER=false

# Run Experiments
N=10 ./util/experiments/hotelreservation/run_all_n.sh

# Run experiments once without SER for ablation
TAG="NO-SER" USE_SER=false N=1 ./util/experiments/hotelreservation/run_all_n.sh
```

### 1.6. "Meta" and Micro Benchmarks

These benchmarks are contained in this repository.
They are run using [testcontainers](https://testcontainers.com/) and require Docker.

```sh
cd <experimentation directory>/reynard

# Uncomment if you intent to use your local maven installation
# export USE_DOCKER=false

# Meta
N=10 ./util/experiments/meta/run_all_n.sh
TAG="NO-SER" USE_SER=false N=1 ./util/experiments/meta/run_all_n.sh # For ablation

# Micro
N=10 ./util/experiments/micro/run_all_n.sh
TAG="NO-SER" USE_SER=false N=1 ./util/experiments/micro/run_all_n.sh # For ablation
```

Tip: These benchmarks can be debugged directly using your IDE if you use reynard locally.

## 2. Comparison with Filibuster

We can compare Reynard with Filibuster using the Filibuster Corpus, a set of microbenchmarks that correspond to common system interactions in microservices. To generate a baseline, we need to run filibuster on its corresponding set of microbenchmarks (_corpus_) with a configuration that matches that of Reynard.

### 2.1. Setup

For a fair comparison, we must re-run Filibuster with the same failure modes as Reynard.
Furthermore, we need to tweak Filibuster slightly to ensure it runs stably.
For this, we have created a fork of Filibuster with the [changes required](https://github.com/delanoflipse/filibuster-comparison/pull/1) to run it.
You can find the [changed version here](https://github.com/delanoflipse/filibuster-comparison/tree/track-changes) (note that it uses a branch). We had to do a similar process to run the corpus. These changes are [tracked here](https://github.com/delanoflipse/filibuster-corpus/pull/3).

To simplify running all experiments, we introduce a script called `run_experiments_n.sh`, which automatically runs all used microbenchmarks (including building them, starting, and stopping) for a configurable number of iterations.
As the logs generated by Filibuster are numerous, we ran them with as few logs as possible to prevent the log writing from influencing the results.

### 2.2. Experiments

To run the whole Filibuster test suite, run:

```sh
cd path/to/some/empty/dir

# Clone patched Filibuster into ./filibuster
git clone -b track-changes --single-branch https://github.com/delanoflipse/filibuster-comparison.git filibuster

# Clone patched Corpus into ./corpus
git clone -b baseline --single-branch https://github.com/delanoflipse/filibuster-corpus.git corpus

# Install filibuster CLI
cd filibuster
poetry install

# Run Experiments
N=10 USE_COLOR=false ./run_experiments_n.sh <optional tag>

# Run Experiments once without SER(=DR) for ablation
DISABLE_DR=1 N=1 USE_COLOR=false ./run_experiments_n.sh NO-SER<-optional tag>
```

### 2.3. Post-processing

The outcome is a dump of logs from the Filibuster runs in the format `results/<benchmark>/<run-id>/filibuster.log`.
At the bottom of this log is a summary of the results (cases and runtime).
To avoid having to extract these values manually, we include a small extract script that extracts these values. These are found [here](./filibuster/extract/).

## 3. Overhead Benchmark

A detailed description of the overhead benchmarks can be found [in its directory](./overhead/).

To run:

```sh
cd <experimentation directory>/reynard
N=10 ./util/experiments/overhead/service_overhead_n.sh

# Or, to run a single test:
TEST=<test-name> ./util/experiments/overhead/service_overhead.sh
```

### 3.1. Post-processing

This logs the output of wrk in `results/overhead/<scenario>/wrk.log` files, which tracks all related results.
In `util/experiments/overhead/extract/`, there are scripts to extract the relevant metrics and calculate averages per metric.
