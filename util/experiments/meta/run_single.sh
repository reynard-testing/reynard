#!/bin/bash
# ------------------------------------------------------------------
# This script runs a single meta experiment.
#
# Usage: ./run_single.sh <benchmark_id>
# Env:
#   TAG: Optional, tag to identify the experiment runs.       (default: "")
#   OUT_DIR: Optional, directory to store results             (default: ./results)
#   USE_SER: Optional, whether to use SER                     (default: true)
#   PROXY_RETRY_COUNT: Optional, number of proxy retries      (default: 3)
#   USE_DOCKER: Optional, whether to use Docker               (default: true)
# ------------------------------------------------------------------

# Required argument
benchmark_id=$1

# Optional environment variables
result_tag=${TAG:-"default"}
use_ser=${USE_SER:-true}
results_dir=${OUT_DIR:-"./results"}
use_docker=${USE_DOCKER:-true}

PROXY_RETRY_COUNT=${PROXY_RETRY_COUNT:-3}
PROXY_IMAGE=${PROXY_IMAGE:-"fit-proxy:latest"}
CONTROLLER_IMAGE=${CONTROLLER_IMAGE:-"fit-controller:latest"}

# Constants
suite_name="MetaSuiteIT"
application_name="meta"

parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
reynard_path=$(realpath "${parent_path}/../../..")

results_dir=$(realpath "${results_dir}")
output_dir="${results_dir}/runs/${application_name}/${result_tag}"
log_dir="${results_dir}/logs/${application_name}/${result_tag}"

# Log and create directories
echo "Storing results in: ${output_dir}"
echo "Storing logs in: ${log_dir}"
echo "Using application path: ${application_path}"

run_log_dir="${log_dir}/${benchmark_id}/"
log_file="${run_log_dir}/${tag}.log"

mkdir -p "${output_dir}"
mkdir -p "${run_log_dir}"

test_name=$(echo "$benchmark_id" | sed -E 's/(^|-)([a-z])/\U\2/g' | tr -d '-')

echo "Running ${benchmark_category} benchmark: ${benchmark_id} (${test_name})"
echo "Storing in ${output_file}"

# Run tests
cd ${reynard_path}

if [ "${use_docker}" = true ]; then
  docker run \
    --rm \
    --network host \
    -v ${output_dir}/:/results/tests \
    -v /var/run/docker.sock:/var/run/docker.sock \
    --add-host host.docker.internal=host-gateway \
    -e OUTPUT_TAG=${tag} \
    -e USE_SER=${use_ser} \
    -e PROXY_RETRY_COUNT=${PROXY_RETRY_COUNT} \
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
  PROXY_RETRY_COUNT=${PROXY_RETRY_COUNT} \
  PROXY_IMAGE=${PROXY_IMAGE} \
  CONTROLLER_IMAGE=${CONTROLLER_IMAGE} \
  mvn test -Dtest=${suite_name}#test${test_name} | tee ${log_file}
fi
