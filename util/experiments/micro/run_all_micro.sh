#!/bin/bash
# ------------------------------------------------------------------
# This script runs all micro experiments in sequence.
#
# Usage: ./run_all_micro.sh [base_tag]
# Env:
#   N: Optional, number of iterations to run (default: 10)
# ------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
base_tag=${1:-""}
iterations=${N:-10}

trap "exit" INT
cd ${SCRIPT_DIR}
for ((i=1; i<=iterations; i++)); do
    OUTPUT_TAG=${base_tag}${i} ./run_full_micro.sh ResiliencePatternsIT testA ${base_tag}${i}
    OUTPUT_TAG=${base_tag}${i} ./run_full_micro.sh ResiliencePatternsIT testCs ${base_tag}${i}
    OUTPUT_TAG=${base_tag}${i} ./run_full_micro.sh ResiliencePatternsIT testOpt ${base_tag}${i}
    OUTPUT_TAG=${base_tag}${i} ./run_full_micro.sh ResiliencePatternsIT testCsOpt ${base_tag}${i}
done
