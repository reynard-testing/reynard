#!/bin/bash
# ------------------------------------------------------------------
# This script runs a astronomy-shop experiments
#
# Usage: ./run_single.sh <benchmark_id>
# Env: BUILD_BEFORE (if set to 1, builds before each run, default 1)

# Env:
#   TAG: Optional, tag to identify the experiment runs.       (default: "")
#   OUT_DIR: Optional, directory to store results             (default: ./results)
#   USE_SER: Optional, whether to use SER                     (default: true)
#   APP_PATH: Optional, path to the application directory.    (default: ../benchmarks/filibuster-corpus)
#   USE_DOCKER: Optional, whether to use Docker               (default: true)
#   BUILD_BEFORE: Optional, whether to build before each run  (default: unset)
#   SKIP_START: Optional, whether to start the benchmark.     (default: unset)
#   STOP_AFTER: Optional, whether to stop the benchmark after (default: unset)
# ------------------------------------------------------------------

# Required argument
benchmark_id=$1

# Optional environment variables
tag=${TAG:-"default"}
use_ser=${USE_SER:-true}
results_dir=${OUT_DIR:-"./results"}
use_docker=${USE_DOCKER:-true}

# Constants
suite_name="OTELSuiteIT"
application_name="astronomy-shop"

# Path setup
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
reynard_path=$(realpath "${parent_path}/../../..")

results_dir=$(realpath "${results_dir}")
output_dir="${results_dir}/runs/${application_name}/${tag}"
log_dir="${results_dir}/logs/${application_name}/${tag}"

application_path=${APP_PATH:-"${reynard_path}/../benchmarks/astronomy-shop"}
application_path=$(realpath "${application_path}")
application_path=${application_path}

test_name=$(echo "$benchmark_id" | sed -E 's/(^|-)([a-z])/\U\2/g' | tr -d '-')

# Check if the application path exists
if [ ! -d "${application_path}" ]; then
  echo "Error: application path ${application_path} does not exist."
  exit 1
fi

# Log and create directories
echo "Storing results in: ${output_dir}"
echo "Storing logs in: ${log_dir}"
echo "Using application path: ${application_path}"

run_log_dir="${log_dir}/${benchmark_id}/"
log_file="${run_log_dir}/${tag}.log"

mkdir -p "${output_dir}"
mkdir -p "${run_log_dir}"

# Build images
cd ${application_path}
if [ -n "${BUILD_BEFORE}" ]; then
  docker compose -f docker-compose.fit.yml build
fi

# Start containers
if [ -z "${SKIP_START}" ]; then
  make start-fit

  # Wait for containers to be healthy
  echo "Waiting for http://localhost:8080/ to be available..."
  until curl -s http://localhost:8080/ > /dev/null; do
    echo "Service not available yet. Retrying in 5 seconds..."
    sleep 5
  done

  echo "Service is available."
else
  echo "Skipping stack startup as SKIP_START is set."
fi

# Run tests
cd ${project_path}

if [ "${use_docker}" = true ]; then
  docker run \
    --rm \
    -v ${output_dir}/:/results/tests \
    --network="host" \
    -e OUTPUT_TAG=${tag} \
    -e USE_SER=${use_ser} \
    fit-library:latest \
    /bin/bash -c "mvn test -Dtest=${suite_name}#test${test_name}" | tee ${log_file}
else
  cd ${reynard_path}; \
    OUTPUT_DIR=${output_dir} \
    OUTPUT_TAG=${tag} \
    USE_SER=${use_ser} \
    mvn test -Dtest=${suite_name}#test${test_name} | tee ${log_file}
fi

# Stop containers
if [ "${STOP_AFTER:-1}" == "1" ]; then
    cd ${application_path}
     docker compose -f docker-compose.fit.yml down
fi
