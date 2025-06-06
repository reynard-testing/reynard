#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}")

result_tag=${1:+-$1}
iterations=${N:-30}

export STOP_AFTER=1
export BUILD_BEFORE=${BUILD_BEFORE:-1}

cd ${project_path}
trap "exit" INT

run_n_benchmark() {
    local benchmark_id=$1
    local extra_env="${@:2}"

    if [ -n "${extra_env}" ]; then
        env_tag=$(echo "${extra_env}" | tr ' ' '_' | tr '=' '__' | tr '-' '_')
        env_tag="${env_tag}-"
    else
        env_tag=""
    fi

    for ((i=1; i<=iterations; i++)); do
        iteration_tag="${result_tag}${env_tag}${i}"
        
        build_before=$(( i == 0 ? 1 : 0 ))
        skip_restart=$(( i > 1 ? 1 : 0 ))
        if [ "$i" -eq "$iterations" ]; then
            stop_after=1
        else
            stop_after=0
        fi


        echo "Running ${benchmark_id} benchmark (iteration $i of $iterations) (tag: $iteration_tag)"
        env ${extra_env} OUTPUT_TAG="${iteration_tag}" SKIP_RESTART=${skip_restart} BUILD_BEFORE=${build_before} STOP_AFTER=${stop_after} ./run_full_filibuster.sh ${benchmark_id} ${iteration_tag}
    done
}

if [ -z "${SKIP_INDUSTRY}" ]; then
    run_n_benchmark "netflix" "WITH_FAULTS=1 NETFLIX_FAULTS=1"
    run_n_benchmark "netflix"
    run_n_benchmark "mailchimp"
    run_n_benchmark "audible"
    # run_n_benchmark "audible" "WITH_FAULTS=1 BAD_METADATA=1"
    run_n_benchmark "expedia"

    # run_n_benchmark "mailchimp" "WITH_FAULTS=1 DB_READ_ONLY=1"
fi

if [ -z "${SKIP_CINEMA}" ]; then
    run_n_benchmark "cinema-1"
    run_n_benchmark "cinema-2"
    run_n_benchmark "cinema-3"
    run_n_benchmark "cinema-3" OPT_RETRIES=1
    run_n_benchmark "cinema-4"
    run_n_benchmark "cinema-5"
    run_n_benchmark "cinema-6"
    run_n_benchmark "cinema-7"
    run_n_benchmark "cinema-8"
    run_n_benchmark "cinema-8" OPT_RETRIES=1
fi
