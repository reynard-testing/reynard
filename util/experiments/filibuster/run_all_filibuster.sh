#!/bin/bash
# ------------------------------------------------------------------
# This script runs all Filibuster experiments once
#
# Usage: ./run_all_filibuster.sh <result_tag>
# Env: SKIP_CINEMA (if set, skips cinema benchmarks)
# Env: SKIP_INDUSTRY (if set, skips industry benchmarks)
# Env: BUILD_BEFORE (if set to 1, builds before each run, default 1)
# ------------------------------------------------------------------

parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}")

result_tag=${1:+-$1}

export STOP_AFTER=1
export BUILD_BEFORE=${BUILD_BEFORE:-1}

cd ${project_path}
trap "exit" INT

if [ -z "${SKIP_CINEMA}" ]; then
    ./run_full_filibuster.sh cinema-1 $1
    ./run_full_filibuster.sh cinema-2 $1
    ./run_full_filibuster.sh cinema-3 $1
    OPT_RETRIES=1 ./run_full_filibuster.sh cinema-3 $1
    ./run_full_filibuster.sh cinema-4 $1
    ./run_full_filibuster.sh cinema-5 $1
    ./run_full_filibuster.sh cinema-6 $1
    ./run_full_filibuster.sh cinema-7 $1
    ./run_full_filibuster.sh cinema-8 $1
    OPT_RETRIES=1 ./run_full_filibuster.sh cinema-8 $1
fi

if [ -z "${SKIP_INDUSTRY}" ]; then
    ./run_full_filibuster.sh audible $1
    ./run_full_filibuster.sh expedia $1
    ./run_full_filibuster.sh mailchimp $1
    ./run_full_filibuster.sh netflix $1
    WITH_FAULTS=1 NETFLIX_FAULTS=1 ./run_full_filibuster.sh netflix faults${result_tag}
fi
