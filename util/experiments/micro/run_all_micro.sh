#!/bin/bash
# ------------------------------------------------------------------
# This script runs all micro experiments in sequence.
#
# Usage: ./run_all_micro.sh [base_tag]
# Env:
#   N: Optional, number of iterations to run (default: 10)
# ------------------------------------------------------------------

base_tag=${1:-""}
iterations=${N:-10}

trap "exit" INT
for ((i=1; i<=iterations; i++)); do
    OUTPUT_TAG=${base_tag}${i} ./run_full_micro.sh ResiliencePatternsIT testA
    OUTPUT_TAG=${base_tag}${i} ./run_full_micro.sh ResiliencePatternsIT testCs
    OUTPUT_TAG=${base_tag}${i} ./run_full_micro.sh ResiliencePatternsIT testOpt
    OUTPUT_TAG=${base_tag}${i} ./run_full_micro.sh ResiliencePatternsIT testCsOpt
done
