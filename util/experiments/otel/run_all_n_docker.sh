#!/bin/bash
# ------------------------------------------------------------------
# This script runs all experiments for the astronomy-shop benchmark using Docker.
#
# Usage: ./run_all_n_docker.sh
# Env:
#   TAG: Optional, tag to identify the experiment runs.       (default: "").
#   OUT_DIR: Optional, directory to store results             (default: ./results).
#   USE_SER: Optional, whether to use SER                     (default: true).
#   APP_PATH: Optional, path to the application directory.   (default: ../benchmarks/astronomy-shop).
#   N: Optional, number of iterations to run                  (default: 10).
# ------------------------------------------------------------------

# Optional environment variables
result_tag=${TAG:-"default"}
iterations=${N:-10}
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
output_dir="${results_dir}/runs/${application_name}/${result_tag}"
log_dir="${results_dir}/logs/${application_name}/${result_tag}"

application_path=${APP_PATH:-"${reynard_path}/../benchmarks/astronomy-shop"}
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

# # Build images and start containers
cd ${application_path}; make start-fit


# Wait for the health check to pass
echo "Waiting for http://localhost:8080/ to be available..."
until curl -s http://localhost:8080/ > /dev/null; do
  echo "Service not available yet. Retrying in 5 seconds..."
  sleep 5
done
echo "Service is available."

# Function to run a single benchmark
run_benchmark() {
  local benchmark_id=$1
  local tag=$2

  run_log_dir="${log_dir}/${benchmark_id}"
  mkdir -p "${run_log_dir}"
  log_file="${run_log_dir}/${tag}.log"

  echo "Running test: ${benchmark_id} with tag: ${tag}, logging to ${log_file}"
  
  test_name=$(echo "$benchmark_id" | sed -E 's/(^|-)([a-z])/\U\2/g' | tr -d '-')

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

}

trap "exit" INT

# Run benchmarks
for ((i=1; i<=iterations; i++)); do
  echo "Running iteration ${i} of ${iterations}"
  run_tag="${result_tag:+${result_tag}-}${i}"

  run_benchmark shipping ${run_tag}
  run_benchmark recommendations ${run_tag}
  run_benchmark recommendationsWithPruner ${run_tag}
  run_benchmark checkout ${run_tag}
  run_benchmark checkoutWithCs ${run_tag}
done

# Stop containers
cd ${application_path}; docker compose -f docker-compose.fit.yml down
