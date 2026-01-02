#!/bin/bash
# ------------------------------------------------------------------
# This script runs a hotel reservation experiments
#
# Usage: ./run_single.sh <benchmark_id>
# Env: BUILD_BEFORE (if set to 1, builds before each run, default 1)

# Env:
#   TAG: Optional, tag to identify the experiment runs.       (default: "")
#   OUT_DIR: Optional, directory to store results             (default: ./results)
#   USE_SER: Optional, whether to use SER                     (default: true)
#   APP_PATH: Optional, path to the application directory.    (default: ../benchmarks/filibuster-corpus)
#   USE_DOCKER: Optional, whether to use Docker               (default: true)
#   BUILD_BEFORE: Optional, whether to build before each run  (default: 1)
#   SKIP_START: Optional, whether to start the benchmark.     (default: 0)
#   STOP_AFTER: Optional, whether to stop the benchmark after (default: 1)
# ------------------------------------------------------------------

# Required argument
benchmark_id=$1

# Optional environment variables
tag=${TAG:-"default"}
use_ser=${USE_SER:-true}
results_dir=${OUT_DIR:-"./results"}
use_docker=${USE_DOCKER:-true}

PROXY_IMAGE=${PROXY_IMAGE:-"fit-proxy:latest"}
CONTROLLER_IMAGE=${CONTROLLER_IMAGE:-"fit-controller:latest"}

# Constants
suite_name="HotelReservationSuiteIT"
application_name="hotelreservation"

# Path setup
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
reynard_path=$(realpath "${parent_path}/../../..")

results_dir=$(realpath "${results_dir}")
output_dir="${results_dir}/runs/${application_name}/${tag}"
log_dir="${results_dir}/logs/${application_name}/${tag}"

application_path=${APP_PATH:-"${reynard_path}/../benchmarks/DeathStarBench/hotelReservation"}
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

run_log_dir="${log_dir}/${benchmark_id}/"
log_file="${run_log_dir}/${tag}.log"

mkdir -p "${output_dir}"
mkdir -p "${run_log_dir}"

compose_envs="PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE}"
test_name=$(echo "$benchmark_id" | sed -E 's/(^|-)([a-z])/\U\2/g' | tr -d '-')

# Build images
cd ${application_path}
if [ "${BUILD_BEFORE:-1}" == "1" ]; then
    env ${compose_envs} docker compose -f docker-compose.fit.yml build
fi

# Start containers
if [ "${SKIP_START:-0}" != "1" ]; then
  env ${compose_envs} docker compose -f docker-compose.fit.yml up -d --force-recreate --remove-orphans

  until curl -s -o /dev/null -w "%{http_code}" http://localhost:5000/ | grep -qv '^5'; do
    echo "Waiting for services to be available..."
    sleep 1
  done

  # Wait for containers to be healthy
  sleep 5
fi

# Run tests
cd ${reynard_path}

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
    env ${compose_envs} docker compose -f docker-compose.fit.yml down
fi
