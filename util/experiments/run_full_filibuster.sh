#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../..")
benchmark_id=$1
benchmark_category="filibuster"
result_tag=${2:+-$2}
result_path="${project_path}/results/${benchmark_category}/${benchmark_id}"
output_file="${result_path}/${benchmark_id}${result_tag}.log"

corpus_path=${CORPUS_PATH:-"${project_path}/../../benchmarks/filibuster-corpus"}
corpus_path=$(realpath "${corpus_path}")
corpus_path=${corpus_path}/${benchmark_id}

test_name=$(echo "$benchmark_id" | sed -E 's/(^|-)([a-z])/\U\2/g' | tr -d '-')

echo "Running ${benchmark_category} benchmark: ${benchmark_id} (${test_name})"
echo "Storing in ${output_file}"
echo "Using corpus path: ${corpus_path}"

# Check if the corpus path exists
if [ ! -d "${corpus_path}" ]; then
  echo "Error: Corpus path ${corpus_path} does not exist."
  exit 1
fi

# Build images
cd ${corpus_path}
if [ -n "${BUILD_BEFORE}" ]; then
    docker compose -f docker-compose.fit.yml build
fi

# Start containers
docker compose -f docker-compose.fit.yml up -d --force-recreate --remove-orphans


# Run tests
cd ${project_path}
mkdir -p ${result_path}
mvn clean test -Dtest=FilibusterSuiteIT#test${test_name} | tee ${output_file}


if [ -n "${STOP_AFTER}" ]; then
    cd ${corpus_path}
    docker compose -f docker-compose.fit.yml down
    exit 0
fi
