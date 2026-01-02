#!/bin/bash
# ------------------------------------------------------------------
# This script runs all experiments for the meta benchmarks.
#
# Usage: ./run_all_n_docker.sh
# Env:
#   TAG: Optional, tag to identify the experiment runs.       (default: "").
#   OUT_DIR: Optional, directory to store results             (default: ./results).
#   USE_SER: Optional, whether to use SER                     (default: true).
#   N: Optional, number of iterations to run                  (default: 10).
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
suite_name="MetaSuiteIT"
application_name="meta"

# Path setup
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
reynard_path=$(realpath "${parent_path}/../../..")

results_dir=$(realpath "${results_dir}")
output_dir="${results_dir}/runs/${application_name}/${result_tag}"
log_dir="${results_dir}/logs/${application_name}/${result_tag}"

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
  local retry_count=$3

  run_log_dir="${log_dir}/${benchmark_id}"
  mkdir -p "${run_log_dir}"
  log_file="${run_log_dir}/${tag}.log"

  echo "Running test: ${benchmark_id} with tag: ${tag}, logging to ${log_file}"
  
  test_name=$(echo "$benchmark_id" | sed -E 's/(^|-)([a-z])/\U\2/g' | tr -d '-')

  if [ "${use_docker}" = true ]; then
    docker run \
      --rm \
      --network host \
      -v ${output_dir}/:/results/tests \
      -v /var/run/docker.sock:/var/run/docker.sock \
      --add-host host.docker.internal=host-gateway \
      -e OUTPUT_TAG=${tag} \
      -e USE_SER=${use_ser} \
      -e PROXY_RETRY_COUNT=${retry_count} \
      -e PROXY_IMAGE=${PROXY_IMAGE} \
      -e CONTROLLER_IMAGE=${CONTROLLER_IMAGE} \
      -e LOCAL_HOST=host.docker.internal \
      -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
      fit-library-dind:latest \
      /bin/bash -c "mvn test -Dtest=${suite_name}#test${test_name}" | tee ${log_file}
  else
    cd ${reynard_path}; \
    OUTPUT_DIR=${output_dir} \
    OUTPUT_TAG=${tag} \
    USE_SER=${use_ser} \
    PROXY_RETRY_COUNT=${retry_count} \
    PROXY_IMAGE=${PROXY_IMAGE} \
    CONTROLLER_IMAGE=${CONTROLLER_IMAGE} \
    mvn test -Dtest=${suite_name}#test${test_name} | tee ${log_file}
  fi
}

trap "exit" INT

# Run benchmarks
for ((i=1; i<=iterations; i++)); do
  echo "Running iteration ${i} of ${iterations}"
  run_tag="${result_tag:+${result_tag}-}${i}"

  run_benchmark register ${run_tag} 1
  run_benchmark register2 ${run_tag} 3
  run_benchmark register4 ${run_tag} 5
done
