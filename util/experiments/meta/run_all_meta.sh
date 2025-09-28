#!/bin/bash
# ------------------------------------------------------------------
# This script runs all meta experiments in sequence.
#
# Usage: ./run_all_meta.sh [base_tag]
# Env:
#   N: Optional, number of iterations to run (default: 30)
# ------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
base_tag=${1:-""}
iterations=${N:-30}

trap "exit" INT
cd ${SCRIPT_DIR}
for ((i=1; i<=iterations; i++)); do
    OUTPUT_TAG=${base_tag}${i} ./run_full_meta.sh register
done
