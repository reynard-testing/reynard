#!/bin/bash
# ------------------------------------------------------------------
# This script runs all meta experiments in sequence.
#
# Usage: ./run_all_meta.sh [base_tag]
# Env:
#   N: Optional, number of iterations to run (default: 30)
#   PROXY_RETRY_COUNT: Optional, number of times to retry proxy requests (default: 3)
# ------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
base_tag=${1:-""}
iterations=${N:-30}

trap "exit" INT
cd ${SCRIPT_DIR}
for ((i=1; i<=iterations; i++)); do
    PROXY_RETRY_COUNT=1 OUTPUT_TAG=${base_tag}${i} ./run_full_meta.sh register ${base_tag}${i}
    PROXY_RETRY_COUNT=3 OUTPUT_TAG=${base_tag}${i} ./run_full_meta.sh register2 ${base_tag}${i}
    PROXY_RETRY_COUNT=5 OUTPUT_TAG=${base_tag}${i} ./run_full_meta.sh register4 ${base_tag}${i}
done
