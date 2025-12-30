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

# Constants
suite_name="FilibusterSuiteIT"
application_name="filibuster-corpus"

# Path setup
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
reynard_path=$(realpath "${parent_path}/../../..")

results_dir=$(realpath "${results_dir}")
output_dir="${results_dir}/runs/${application_name}/${result_tag}"
log_dir="${results_dir}/logs/${application_name}/${result_tag}"

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

run_benchmark_n() {
  local application_id=$1
  local extra_env=$2

  if [ -n "${extra_env}" ]; then
      env_tag=$(echo "${extra_env}" | tr ' ' '_' | tr '=' '__' | tr '-' '_')
      env_tag="${env_tag}-"
  else
      env_tag=""
  fi

# TODO start
  
  for ((i=1; i<=iterations; i++)); do
    iteration_tag="${result_tag}${env_tag}${i}"
    run_benchmark ${application_id} ${iteration_tag}
  done
  # TODO stop
}

trap "exit" INT

# Run benchmarks
run_benchmark_n cinema-1
run_benchmark_n cinema-2
run_benchmark_n cinema-3
run_benchmark_n cinema-3 OPT_RETRIES=1
run_benchmark_n cinema-4
run_benchmark_n cinema-5
run_benchmark_n cinema-6
run_benchmark_n cinema-7
run_benchmark_n cinema-8
run_benchmark_n cinema-8 OPT_RETRIES=1

run_benchmark_n expedia
run_benchmark_n audible
run_benchmark_n mailchimp
run_benchmark_n netflix
run_benchmark_n "netflix" "WITH_FAULTS=1 NETFLIX_FAULTS=1"