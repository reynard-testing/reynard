#!/bin/bash
# ------------------------------------------------------------------
# This script runs all service overhead experiments for the astronomy-shop benchmark.
#
# Usage: ./service_overhead_n.sh [result_tag]
# Env:
#   CONTROLLER_PORT: Optional, port for the controller service (default: 5050).
#   TEST: Optional, run only a specific test case.
#   TEST_DURATION: Optional, duration for wrk load test (default: 1m).
#   MAX_CONNECTIONS: Optional, max connections for wrk (default: 4).
#   THREADS: Optional, number of threads for wrk (default: 4).
# ------------------------------------------------------------------

parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../../..")
benchmark_category="overhead"
result_tag=${1:-default}

iterations=${N:-10}

trap "exit" INT
cd ${parent_path}
for ((i=1; i<=iterations; i++)); do
    ./service_overhead.sh ${result_tag}${i}
done
