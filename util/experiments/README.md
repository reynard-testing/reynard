# Reynard Experimentation

This directory contains everything to run Reynard in different scenarios for specific benchmarks.
This includes scripts to simplify the experimentation process, as well as this description.

### General notes

- Most experiment scripts expect dependent projects to be present at a _specific_ path relative to their own. Be careful in the relative location and naming of checked out repositories. Moreover, do not move scripts, as they will use paths relative to their location.
- The file [junit-platform.properties](/library/src/test/resources/junit-platform.properties) defines _where_ the Reynard results are put. Alternatively, you can run reynard with a `OUTPUT_DIR` environment variable set.

## Requirements

### Software Requirements

- All experiments require an installation of `Docker` and `Docker Compose`.
- All script require `bash`. Use a linux distribution, or bash for windows.
- Most build processes make use of `make` (Makefiles).
- (Optional) To run reynard locally requires `Java JDK 17` and `Maven`. We offer a dockerised version too.

### Hardware Requirements

The benchmarks require running a (benchmark) microservice application on a single machine. Therefore, they need to be run on machines with enough resources, especially for the Astronomy Shop benchmark. As a reference, we were able to reproduce the experiments on the following hardware specs:

| CPU                                  | Memory    | OS                                     | Used in results: |
| ------------------------------------ | --------- | -------------------------------------- | ---------------- |
| AMD Ryzen 7 3700X (8 cores @3.6 GHz) | 32GB DDR4 | Windows 11 Pro<br>WSL Ubuntu 20.04 LTS | ✔               |
| Intel i7-6700HQ (4 cores @3.4 GHz)   | 16GB DDR4 | Windows 10 Pro<br>WSL Ubuntu 20.04 LTS |                  |

## Artifacts

If you are only interested in the raw data used to generate the results, please look at the [artifacts repository](https://github.com/reynard-testing/experiment-artifacts).
It contains the raw logs used in the results, as well as relevant post-processing results.
Note that, for ease of finding files, we've renamed or moved a small number of files.

## Post-processing

To process the raw data for both visualisation and analytics of a Reynard run, we provide a number of post-processing scripts in `util/viz`.
A description can be found [here](../viz/).

# Experiments

We have three experiments that we use in the results, which we detail in the following sections:

1. Running Reynard on the benchmark systems (including the Filibuster Corpus).
2. Running Filibuster on its corpus with a configuration that matches Reynard.
3. A stress-test on the Reynard instrumentation.

These will be detailed below.

Note that reproducing all results takes a long time: the main results take around 6 hours to reproduce, it takes around 5 hours for the comparison with filibuster, and around 2.5 hours for the overhead experiments (for n=10). Even taking n=1 will take hours, as building steps take a significant amount of time.
To verify if you can run the experiments, we recommend reproducing a single iteration of the "Reynard Register" test scenario. This can be done by following the steps in [section 1.1.0](#110-running-a-minimal-setup-optional).

## 1. Running Reynard on the benchmarks

This experiment requires Reynard, as well as the benchmark systems it should run on. These benchmark systems are (based on):

1. [Filibuster Corpus](https://github.com/filibuster-testing/filibuster-corpus)' set of microbenchmarks
1. [DeathStarBench](https://github.com/delimitrou/DeathStarBench)'s hotelReservation
1. OpenTelemetry's [Astronomy shop](https://github.com/open-telemetry/opentelemetry-demo)
1. Microbenchmarks included in Reynard
1. Reynard itself (we refer to these tests as "meta" tests)

### 1.1. Installation

#### 1.1.0. Running a minimal setup (optional)

To verify if you can run the experiments, we recommend reproducing a single iteration of the "Reynard Register" test scenario. This can be done by performing the following:

- Clone the repository in an empty directory: `git clone --single-branch https://github.com/reynard-testing/reynard.git reynard` (similar to [1.1.1.](#111-cloning-repositories))
- Change directory to the reynard folder (`cd reynard`) and build the instrumentation images and the testing library in docker images using `make build-all`. This takes roughly 3-4 minutes to build.
- Run the following script to perform a single execution of the register benchmark: `./util/experiments/meta/run_single.sh register`. This should take roughly one minute to execute, and should result in “729 tests run”.
- The results should now be present in the `results` directory relative to where you ran the script from.
- Make sure this results directory is not the same as the directory where the complete results are stored, as they might influence each other if they end up in the same run directory. Optionally, you can remove the results directory to be sure.

#### 1.1.1. Cloning repositories

The experiment scripts in this repository expect this file structure:

```sh
|- reynard # (this repository)
|- benchmarks/ # (the different benchmark systems)
```

To run Reynard on the the benchmark systems, we have to tweak them to enable our instrumentation.
To get all required repositories, you run the following [script](./setup/01_clone.sh) in an **empty** directory.
The script runs the following commands:

```bash
cd <emtpy experimentation directory>

# Clone Reynard repository
git clone --single-branch https://github.com/reynard-testing/reynard.git reynard

# Clone benchmarks
git clone -b reynard-changes --single-branch https://github.com/delanoflipse/filibuster-corpus.git benchmarks/filibuster-corpus
git clone -b track-changes --single-branch  https://github.com/delanoflipse/opentelemetry-demo-ds-fit.git benchmarks/astronomy-shop
git clone -b fit-instrumentation --single-branch https://github.com/delanoflipse/DeathStarBench.git benchmarks/DeathStarBench
```

Note: This will take roughly 30 seconds, depending on your internet speed.

#### 1.1.2. Building Docker images

All benchmarks are _large_ docker-based microservice applicatons.
We want to build the last version of them, and run them with reynard instrumentation.
To build _all_ required docker images, run the following [script](./setup/02_build.sh) in an the experiment directory.
It runs the following commands:

```bash
cd <experimentation directory>

# Build Reynard images
cd ./reynard; make build-all # (~3m30s)

# Most docker compose files use these environment variables, so provide them
export PROXY_IMAGE=${PROXY_IMAGE:-fit-proxy:latest}
export CONTROLLER_IMAGE=${CONTROLLER_IMAGE:-fit-controller:latest}

# Astronomy shop
cd ./benchmarks/astronomy-shop; (docker compose -f docker-compose.fit.yml build) # (~7m30s)

# DeathStarBench hotelReservation
cd ./benchmarks/DeathStarBench/hotelReservation; (docker compose -f docker-compose.fit.yml build) # (~1m30s)

# Filibuster corpus (~20-30min!)
cd ./benchmarks/filibuster-corpus/cinema-1; (docker compose -f docker-compose.fit.yml build)   # (~2m30s)
cd ./benchmarks/filibuster-corpus/cinema-2; (docker compose -f docker-compose.fit.yml build)   # (~2m30s)
cd ./benchmarks/filibuster-corpus/cinema-3; (docker compose -f docker-compose.fit.yml build)   # (~2m30s)
cd ./benchmarks/filibuster-corpus/cinema-4; (docker compose -f docker-compose.fit.yml build)   # (~2m30s)
cd ./benchmarks/filibuster-corpus/cinema-5; (docker compose -f docker-compose.fit.yml build)   # (~2m30s)
cd ./benchmarks/filibuster-corpus/cinema-6; (docker compose -f docker-compose.fit.yml build)   # (~2m30s)
cd ./benchmarks/filibuster-corpus/cinema-7; (docker compose -f docker-compose.fit.yml build)   # (~2m30s)
cd ./benchmarks/filibuster-corpus/cinema-8; (docker compose -f docker-compose.fit.yml build)   # (~2m30s)
cd ./benchmarks/filibuster-corpus/audible; (docker compose -f docker-compose.fit.yml build)    # (~2m30s)
cd ./benchmarks/filibuster-corpus/expedia; (docker compose -f docker-compose.fit.yml build)    # (~2m30s)
cd ./benchmarks/filibuster-corpus/mailchimp; (docker compose -f docker-compose.fit.yml build)  # (~2m30s)
cd ./benchmarks/filibuster-corpus/netflix; (docker compose -f docker-compose.fit.yml build)    # (~2m30s)
```

Note: This will take **a long time** to complete (in the order of 30-40 minutes...).
If you are only interested in a particular benchmark, you can pick and choose the corresponding build steps.

#### 1.1.3. Building Reynard (optional)

We offer a dockerised runtime for reynard.
However, for the results we ran the experiments locally to minimise any potential overhead.
For this, you need Java JDK 17, maven, and you need to install Reynard using the following command:

```sh
cd <experimentation directory>/reynard
make install # Build library (~5s)
```

#### 1.1.4. Running all experiments at once (optional)

If you want to regenerate _all_ experimental results, you can use the following [script](./setup/03_run.sh).
We will detail each individual benchmark below.

### 1.2. Post-processing

Each test suite will result in several logs placed into `<results-dir>/<benchmark>/<run-id>/..`.
By default, the results directory is `<working-directory>/results`.
The post-processing scripts assumes that all folders for different iterations of the same test scenario to be next to each other.

After all benchmarks have been run, you can use the `util/viz` scripts to post-process the results, as [described here](../viz/).

### 1.3 Filibuster Corpus

We combined all Reynard Filibuster Corpus experiments in the [FilibusterSuiteIT](/library/src/test/java/dev/reynard/junit/integration/FilibusterSuiteIT.java) test suite.

We can use [the provided scripts](./filibuster/) to automatically build, start, and stop each benchmark.
To run the whole suite, run:

```sh
cd <experimentation directory>

# Uncomment if you intent to use your local maven installation
# export USE_DOCKER=false

N=10 ./reynard/util/experiments/filibuster/run_all_n.sh # (~7m30s of overhead + ~15m per iteration)

# Run experiments once without SER for ablation
TAG="NO-SER" USE_SER=false N=1 ./reynard/util/experiments/filibuster/run_all_n.sh  # (~7m30s of overhead + ~30m)
```

Note: The two netflix scenarios take significantly longer to run. Furthermore, if you have less time, you can use the `run_single.sh <benchmark-id>` script to run a single scenario instead.

Tip: if you want to debug an execution, you can follow the steps to start one of the benchmarks and then debug the corresponding test method in your IDE if you use reynard locally.

### 1.4. Astronomy shop

```sh
cd <experimentation directory>/reynard

# Uncomment if you intent to use your local maven installation
# export USE_DOCKER=false

# Run Experiments
N=10 ./reynard/util/experiments/otel/run_all_n.sh # (~1m30s of overhead + ~45s per iteration)

# Run experiments once without SER for ablation
TAG="NO-SER" USE_SER=false N=1 ./reynard/util/experiments/otel/run_all_n.sh # (~ 1m30s of overhead + ~55s per iteration)
```

### 1.5. DeathStarBench

```sh
cd <experimentation directory>/reynard

# Uncomment if you intent to use your local maven installation
# export USE_DOCKER=false

# Run Experiments
N=10 ./reynard/util/experiments/hotelreservation/run_all_n.sh # (~ 1m30s of overhead + 4s per iteration)

# Run experiments once without SER for ablation
TAG="NO-SER" USE_SER=false N=1 ./reynard/util/experiments/hotelreservation/run_all_n.sh # (~ 1m30s of overhead + 5s per iteration)
```

### 1.6. "Meta" and Micro Benchmarks

These benchmarks are contained in this repository.
They are run using [testcontainers](https://testcontainers.com/) and require Docker.
To run them dockerised, we use a Docker out of Docker setup, where the test runner has access to the _outer_ docker service.

```sh
cd <experimentation directory>/reynard

# Uncomment if you intent to use your local maven installation
# export USE_DOCKER=false

# Meta
N=10 ./reynard/util/experiments/meta/run_all_n.sh # (~2m30s per iteration)
TAG="NO-SER" USE_SER=false N=1 ./reynard/util/experiments/meta/run_all_n.sh # For ablation, (~2m30s iteration)

# Micro
N=10 ./reynard/util/experiments/micro/run_all_n.sh # (~5m per iteration)
TAG="NO-SER" USE_SER=false N=1 ./reynard/util/experiments/micro/run_all_n.sh # For ablation (~12m per iteration)
```

Note: For the "meta" test, the images used are already built when we ran `make build-all`, but the micro benchmark docker images have to be built the first time they are executed (and will be reused after), so this will take a while (1-2 minutes) during which the script seems unresponsive.

Tip: These benchmarks can be debugged directly using your IDE if you use reynard locally.

## 2. Comparison with Filibuster

We can compare Reynard with Filibuster using the Filibuster Corpus, a set of microbenchmarks that correspond to common system interactions in microservices. To generate a baseline, we need to run Filibuster on its corresponding set of microbenchmarks (_corpus_) with a configuration that matches that of Reynard.

For a fair comparison, we must re-run Filibuster with the same failure modes as Reynard.
Furthermore, we need to tweak Filibuster slightly to ensure it runs stably.
For this, we have created a fork of Filibuster with the [changes required](https://github.com/delanoflipse/filibuster-comparison/pull/1) to run it.
You can find the [changed version here](https://github.com/delanoflipse/filibuster-comparison/tree/track-changes) (note that it uses a branch). We had to do a similar process to run the corpus. These changes are [tracked here](https://github.com/delanoflipse/filibuster-corpus/pull/3).

To simplify running all experiments, we introduce a script called `run_experiments_n.sh`, which automatically runs all used microbenchmarks (including building them, starting, and stopping) for a configurable number of iterations.
As the logs generated by Filibuster are numerous, we ran them with as few logs as possible to prevent the log writing from influencing the results.

### 2.1. Setup

This experiment requires:

- [poetry](https://python-poetry.org/) as a python package manager.

To prepare for the experiment, run the following:

```sh
cd <empty filibuster experiment dir>

# Clone patched Filibuster into ./filibuster
git clone -b track-changes --single-branch https://github.com/delanoflipse/filibuster-comparison.git filibuster

# Clone patched Corpus into ./corpus
git clone -b baseline --single-branch https://github.com/delanoflipse/filibuster-corpus.git corpus

# Install filibuster CLI
cd filibuster
poetry install
```

### 2.2. Experiments

To run the whole Filibuster test suite, run:

```sh
cd <filibuster experiment dir>/filibuster

# Run Experiments
N=10 USE_COLOR=false ./run_experiments_n.sh <optional tag>

# Run Experiments once without SER(=DR) for ablation
DISABLE_DR=1 N=1 USE_COLOR=false ./run_experiments_n.sh NO-SER<-optional tag>
```

Note: this will build the docker compose setup for each benchmark system, which will take a few minutes per benchmark, and it takes around 25 minutes to do a single iteration of each benchmark test.

### 2.3. Post-processing

The outcome is a dump of logs from the Filibuster runs in the format `results/<benchmark>/<run-id>/filibuster.log`.
At the bottom of this log is a summary of the results (cases and runtime).
To avoid having to extract these values manually, we include a small extract script that extracts these values. These are found [here](./filibuster/extract/).

## 3. Overhead Benchmark

A detailed description of the overhead benchmarks can be found [in its directory](./overhead/).

### 3.1. Setup

These tests run using Docker and Docker Compose.
As we are stress-testing the network throughput, we often incountered issues with the system running out of ephemeral TCP ports.
All containers in the test setup have their net configuration setup to allow for higher throughput. Still, this is influenced by the ports available in the host.

To ensure the host has enough ephemeral ports, please run [this script](./overhead/setup_experiment.sh). It **temporarily** changes the network settings.

### 3.1. Experiments

To run all test scenarios, use:

```sh
cd <experimentation directory>/reynard
N=10 ./reynard/util/experiments/overhead/service_overhead_n.sh

# Or, to run a single test:
TEST=<test-name> ./reynard/util/experiments/overhead/service_overhead.sh
```

Note: this will run around 12 tests, each having an overhead of ~30sec, and runs for a 1min each, so around 15 minutes per iteration.

### 3.3. Post-processing

This logs the output of wrk in `results/overhead/<scenario>/wrk.log` files, which tracks all related results.
In `util/experiments/overhead/extract/`, there are scripts to extract the relevant metrics and calculate averages per metric.
