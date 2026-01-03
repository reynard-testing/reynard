#!/bin/bash
# ------------------------------------------------------------------
# This script runs all experiments for the filibuster corpus.
#
# Usage: ./run_all_n_docker.sh
# Env:
#   TAG: Optional, tag to identify the experiment runs.       (default: "").
#   OUT_DIR: Optional, directory to store results             (default: ./results).
#   USE_SER: Optional, whether to use SER                     (default: true).
#   APP_PATH: Optional, path to the application directory.    (default: ../benchmarks/filibuster-corpus).
#   N: Optional, number of iterations to run                  (default: 10).
#   USE_DOCKER: Optional, whether to use Docker               (default: true).
# ------------------------------------------------------------------

# Optional environment variables
result_tag=${TAG:-"default"}
iterations=${N:-10}
use_ser=${USE_SER:-true}
results_dir=${OUT_DIR:-"./results"}
use_docker=${USE_DOCKER:-true}

PROXY_IMAGE=${PROXY_IMAGE:-"fit-proxy:latest"}
CONTROLLER_IMAGE=${CONTROLLER_IMAGE:-"fit-controller:latest"}

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

base_env="PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE}"

# Function to run a single benchmark
run_benchmark() {
  local benchmark_id=$1
  local test_name=$2
  local tag=$3

  run_log_dir="${log_dir}/${benchmark_id}"
  mkdir -p "${run_log_dir}"
  log_file="${run_log_dir}/${tag}.log"

  echo "Running test: ${test_name} for ${benchmark_id} with tag: ${tag}, logging to ${log_file}"

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
    OUTPUT_TAG=${tag} \
    USE_SER=${use_ser} \
    OUTPUT_DIR=${output_dir} \
    mvn test -Dtest=${suite_name}#test${test_name} | tee ${log_file}
  fi
}

run_benchmark_n() {
  local application_id=$1
  local test_name=$2
  local extra_env=$3

  envs="$base_env ${extra_env}"

  if [ -n "${extra_env}" ]; then
      env_tag=$(echo "${extra_env}" | tr ' ' '_' | tr '=' '__' | tr '-' '_')
      env_tag="${env_tag}-"
  else
      env_tag=""
  fi

  corpus_path="${application_path}/${application_id}"
  echo "Using corpus path: ${corpus_path}"
  echo "Using envs: ${envs}"

  cd ${corpus_path}; env ${envs} docker compose -f docker-compose.fit.yml up -d --force-recreate --remove-orphans
  
  for ((i=1; i<=iterations; i++)); do
    iteration_tag="${result_tag}${env_tag}${i}"
    run_benchmark ${application_id} ${test_name} ${iteration_tag}
  done

  cd ${corpus_path}; env ${envs} docker compose -f docker-compose.fit.yml down
}

trap "exit" INT

# Run benchmarks
run_benchmark_n cinema-1 Cinema1
run_benchmark_n cinema-2 Cinema2
run_benchmark_n cinema-3 Cinema3
run_benchmark_n cinema-3 Cinema3Retries
run_benchmark_n cinema-4 Cinema4
run_benchmark_n cinema-5 Cinema5
run_benchmark_n cinema-6 Cinema6
run_benchmark_n cinema-7 Cinema7
run_benchmark_n cinema-8 Cinema8
run_benchmark_n cinema-8 Cinema8Retries

run_benchmark_n expedia Expedia
run_benchmark_n audible Audible
run_benchmark_n mailchimp Mailchimp
run_benchmark_n netflix Netflix
run_benchmark_n netflix NetflixFaults "NETFLIX_FAULTS=1"