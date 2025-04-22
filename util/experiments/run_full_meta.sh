#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../..")
benchmark_id=$1
benchmark_category="meta"
result_tag=${2:+-$2}
result_path="${project_path}/results/${benchmark_category}/${benchmark_id}"
output_file="${result_path}/${benchmark_id}${result_tag}.log"

test_name=$(echo "$benchmark_id" | sed -E 's/(^|-)([a-z])/\U\2/g' | tr -d '-')

echo "Running ${benchmark_category} benchmark: ${benchmark_id} (${test_name})"
echo "Storing in ${output_file}"


# Build images
cd ${project_path}
if [ -n "${BUILD_BEFORE}" ]; then
  make build-all
fi

# Run tests
cd ${project_path}
mkdir -p ${result_path}
mvn clean test -Dtest=MetaSuiteIT#test${test_name} | tee ${output_file}
