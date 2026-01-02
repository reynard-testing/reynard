#!/bin/bash
# ------------------------------------------------------------------
# This script runs a single micro benchmark experiment.
#
# Usage: ./run_full_micro.sh <suite_name> <test_name> [result_tag]
# ------------------------------------------------------------------

parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../../..")
suite_name=$1
test_name=$2
benchmark_category="micro"
result_tag=${3:+-$3}
result_path="${project_path}/results/logs/${benchmark_category}/${suite_name}#${test_name}"
output_file="${result_path}/${suite_name}#${test_name}${result_tag}.log"

echo "Running ${benchmark_category} benchmark: ${benchmark_id} (${test_name})"
echo "Storing in ${output_file}"

# Run tests
cd ${project_path}
mkdir -p ${result_path}

PROXY_IMAGE=${PROXY_IMAGE:-"fit-proxy:latest"}
CONTROLLER_IMAGE=${CONTROLLER_IMAGE:-"fit-controller:latest"}
PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} mvn clean test -Dtest=${suite_name}#${test_name} | tee ${output_file}