#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../..")
benchmark_id=$1
benchmark_category="filibuster"
result_tag=${2:+-$2}
result_path="${project_path}/results/${benchmark_category}/${benchmark_id}"
run_id=$(date +%Y_%m_%d__%H_%M_%S)
output_file="${result_path}/${benchmark_id}_${run_id}${result_tag}.log"

echo "Running ${benchmark_category} benchmark: ${benchmark_id}"
echo "Storing in ${output_file}"

mkdir -p ${result_path}
mvn test clean -Dtest=FilibusterSuiteIT#test${benchmark_id} | tee ${output_file}