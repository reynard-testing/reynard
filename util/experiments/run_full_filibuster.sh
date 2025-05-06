#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../..")
benchmark_id=$1
benchmark_category="filibuster"
result_tag=${2:+-$2}
result_path="${project_path}/results/${benchmark_category}/${benchmark_id}"

corpus_path=${CORPUS_PATH:-"${project_path}/../../benchmarks/filibuster-corpus"}
corpus_path=$(realpath "${corpus_path}")
corpus_path=${corpus_path}/${benchmark_id}

test_name=$(echo "$benchmark_id" | sed -E 's/(^|-)([a-z])/\U\2/g' | tr -d '-')

if [ -n "${OPT_RETRIES}" ]; then
  test_name="${test_name}Retries"
  result_path="${project_path}/results/${benchmark_category}/${benchmark_id}-retries"
fi

output_file="${result_path}/${benchmark_id}${result_tag}.log"

echo "Running ${benchmark_category} benchmark: ${benchmark_id} (${test_name})"
echo "Storing in ${output_file}"
echo "Using corpus path: ${corpus_path}"

# Check if the corpus path exists
if [ ! -d "${corpus_path}" ]; then
  echo "Error: Corpus path ${corpus_path} does not exist."
  exit 1
fi

PROXY_IMAGE=${PROXY_IMAGE:-"fit-proxy:latest"}
CONTROLLER_IMAGE=${CONTROLLER_IMAGE:-"fit-controller:latest"}

# Build images
cd ${corpus_path}
if [ -n "${BUILD_BEFORE}" ]; then
    PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml build
fi

# Start containers
PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml up -d --force-recreate --remove-orphans

# Wait for containers to be healthy
sleep 3

# Run tests
cd ${project_path}
mkdir -p ${result_path}
mvn clean test -Dtest=FilibusterSuiteIT#test${test_name} | tee ${output_file}


if [ -n "${STOP_AFTER}" ]; then
    cd ${corpus_path}
    PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml down
    exit 0
fi
