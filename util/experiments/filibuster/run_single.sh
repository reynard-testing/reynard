#!/bin/bash
# ------------------------------------------------------------------
# This script runs all Filibuster experiments once
#
# Usage: ./run_all_filibuster.sh <benchmark_id>
# Env: BUILD_BEFORE (if set to 1, builds before each run, default 1)

# Env:
#   TAG: Optional, tag to identify the experiment runs.       (default: "").
#   OUT_DIR: Optional, directory to store results             (default: ./results).
#   USE_SER: Optional, whether to use SER                     (default: true).
#   APP_PATH: Optional, path to the application directory.    (default: ../benchmarks/filibuster-corpus).
#   USE_DOCKER: Optional, whether to use Docker               (default: true).
#   BUILD_BEFORE: Optional, whether to build before each run  (default: 1).
#   SKIP_START: Optional, whether to start the benchmark.     (default: 0).
#   STOP_AFTER: Optional, whether to stop the benchmark after (default: 1).
# ------------------------------------------------------------------

# Required argument
benchmark_id=$1
test_name=$2

# Optional environment variables
result_tag=${TAG:-"default"}
use_docker=${USE_DOCKER:-true}
use_ser=${USE_SER:-true}
results_dir=${OUT_DIR:-"./results"}

PROXY_IMAGE=${PROXY_IMAGE:-"fit-proxy:latest"}
CONTROLLER_IMAGE=${CONTROLLER_IMAGE:-"fit-controller:latest"}

# Constants
suite_name="FilibusterSuiteIT"
application_name="filibuster-corpus"

# Path setup
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
reynard_path=$(realpath "${parent_path}/../../..")

application_path=${APP_PATH:-"${reynard_path}/../benchmarks/filibuster-corpus"}
application_path=$(realpath "${application_path}")
application_path=${application_path}

# Check if the application path exists
if [ ! -d "${application_path}" ]; then
  echo "Error: application path ${application_path} does not exist."
  exit 1
fi

# Log and create directories
echo "Storing results in: ${output_dir}"
echo "Storing logs in: ${log_dir}"
echo "Using application path: ${application_path}"

mkdir -p "${log_dir}"
mkdir -p "${output_dir}"


output_file="${result_path}/${benchmark_id}${result_tag}.log"


# Check if the corpus path exists
if [ ! -d "${corpus_path}" ]; then
  echo "Error: Corpus path ${corpus_path} does not exist."
  exit 1
fi

# Build images
cd ${corpus_path}
if [ "${BUILD_BEFORE:-1}" == "1" ]; then
    PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml build
fi

# Start containers
if [ "${SKIP_START:-0}" != "1" ]; then
  PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml up -d --force-recreate --remove-orphans

  until curl -s -o /dev/null -w "%{http_code}" http://localhost:5001/ | grep -qv '^5'; do
    echo "Waiting for services to be available..."
    sleep 1
  done

  # Wait for containers to be healthy
  sleep 5
fi


# Run tests
cd ${project_path}
mkdir -p ${result_path}

if [ "${use_docker}" = true ]; then
  docker run \
    --rm \
    -v ${output_dir}/:/results/tests \
    --network="host" \
    -e OUTPUT_TAG=${tag} \
    -e USE_SER=${use_ser} \
    fit-library:latest \
    /bin/bash -c "mvn test -Dtest=FilibusterSuiteIT#test${test_name}" | tee ${output_file}
  return
else
  cd ${reynard_path}; \
  OUTPUT_TAG=${tag} \
  USE_SER=${use_ser} \
  OUTPUT_DIR=${output_dir} \
  mvn test -Dtest=FilibusterSuiteIT#test${test_name} | tee ${output_file}
fi

if [ "${STOP_AFTER:-1}" == "1" ]; then
    cd ${corpus_path}
    PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml down
    exit 0
fi
