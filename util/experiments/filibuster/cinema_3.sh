#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../../..")
benchmark_id="cinema_3"
benchmark_category="filibuster"
result_path="${project_path}/results/${benchmark_category}/${benchmark_id}"
run_id=$(date +%Y_%m_%d__%H_%M_%S)

echo "Running ${benchmark_category} benchmark: ${benchmark_id}"
echo "Storing in ${result_path}"

mkdir -p ${result_path}
mvn test clean -Dtest=FilibusterSuiteIT#testCinema3 | tee ${result_path}/${benchmark_id}.${run_id}.log