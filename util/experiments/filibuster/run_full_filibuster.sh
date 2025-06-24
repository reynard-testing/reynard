#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../../..")
benchmark_id=$1
benchmark_category="filibuster"
result_tag=$2
result_path="${project_path}/results/${benchmark_category}/${benchmark_id}"

corpus_path=${CORPUS_PATH:-"${project_path}/../../benchmarks/filibuster-corpus"}
corpus_path=$(realpath "${corpus_path}")
corpus_path=${corpus_path}/${benchmark_id}

test_name=$(echo "$benchmark_id" | sed -E 's/(^|-)([a-z])/\U\2/g' | tr -d '-')

if [ -n "${OPT_RETRIES}" ]; then
  test_name="${test_name}Retries"
  result_path="${project_path}/results/${benchmark_category}/${benchmark_id}-retries"
fi

if [ -n "${WITH_FAULTS}" ]; then
  test_name="${test_name}Faults"
  result_path="${project_path}/results/${benchmark_category}/${benchmark_id}-faults"
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
if [ "${BUILD_BEFORE:-1}" == "1" ]; then
    PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml build
fi

# Start containers
if [ "${SKIP_RESTART:-0}" != "1" ]; then
  PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml up -d --force-recreate --remove-orphans

  until curl -s -o /dev/null -w "%{http_code}" http://localhost:5001/ | grep -qv '^5'; do
    echo "Waiting for services to be available..."
    sleep 1
  done

  # Wait for containers to be healthy
  sleep 5
fi


# Run tests
cd ${project_path}
mkdir -p ${result_path}
OUTPUT_TAG=$result_tag mvn clean test -Dtest=FilibusterSuiteIT#test${test_name} | tee ${output_file}

if [ "${STOP_AFTER:-1}" == "1" ]; then
    cd ${corpus_path}
    PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml down
    exit 0
fi
