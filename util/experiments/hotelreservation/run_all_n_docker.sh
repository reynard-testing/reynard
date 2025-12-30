#!/bin/bash
# ------------------------------------------------------------------
# This script runs all experiments for the astronomy-shop benchmark using Docker.
#
# Usage: ./run_all_n_docker.sh
# Env:
#   TAG: Optional, tag to identify the experiment runs.       (default: "").
#   OUT_DIR: Optional, directory to store results             (default: ./results).
#   USE_SER: Optional, whether to use SER                     (default: true).
#   APP_PATH: Optional, path to the application directory.   (default: ../benchmarks/DeathStarBench/hotelReservation).
#   N: Optional, number of iterations to run                  (default: 10).
# ------------------------------------------------------------------

# Optional environment variables
result_tag=${TAG:-"default"}
iterations=${N:-10}
use_ser=${USE_SER:-true}
results_dir=${OUT_DIR:-"./results"}

PROXY_IMAGE=${PROXY_IMAGE:-"fit-proxy:latest"}
CONTROLLER_IMAGE=${CONTROLLER_IMAGE:-"fit-controller:latest"}

# Constants
suite_name="HotelReservationSuiteIT"
application_name="hotelreservation"

# Path setup
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
reynard_path=$(realpath "${parent_path}/../../..")

results_dir=$(realpath "${results_dir}")
output_dir="${results_dir}/runs/${application_name}/${result_tag}"
log_dir="${results_dir}/logs/${application_name}/${result_tag}"

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

mkdir -p "${log_dir}"
mkdir -p "${output_dir}"

# # Build images and start containers
cd ${application_path}; PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml up -d --force-recreate --remove-orphans


# Wait for the health check to pass
echo "Waiting for http://localhost:5000/ to be available..."
until  curl -s -o /dev/null -w "%{http_code}" http://localhost:5000/ | grep -qv '^5';  do
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

  docker run \
    --rm \
    -v ${output_dir}/:/results/tests \
    --network="host" \
    -e OUTPUT_TAG=${tag} \
    -e USE_SER=${use_ser} \
    fit-library:latest \
    /bin/bash -c "mvn test -Dtest=${suite_name}#test${test_name}" | tee ${log_file}
}

trap "exit" INT

# Run benchmarks
for ((i=1; i<=iterations; i++)); do
  echo "Running iteration ${i} of ${iterations}"
  run_tag="${result_tag:+${result_tag}-}${i}"

  run_benchmark SearchHotels ${run_tag}
  run_benchmark Recommend ${run_tag}
  run_benchmark Recommend ${run_tag}
  run_benchmark Reserve ${run_tag}
  run_benchmark Login ${run_tag}
done

# Stop containers
cd ${application_path}; PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml down
