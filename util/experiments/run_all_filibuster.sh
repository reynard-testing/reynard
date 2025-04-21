#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}")

export STOP_AFTER=1
export BUILD_BEFORE=${BUILD_BEFORE:-1}

cd ${project_path}
trap "exit" INT

if [ -z "${SKIP_CINEMA}" ]; then
    ./run_full_filibuster.sh cinema-1
    ./run_full_filibuster.sh cinema-2
    ./run_full_filibuster.sh cinema-3
    ./run_full_filibuster.sh cinema-4
    ./run_full_filibuster.sh cinema-5
    ./run_full_filibuster.sh cinema-6
    ./run_full_filibuster.sh cinema-7
    ./run_full_filibuster.sh cinema-8
fi

if [ -z "${SKIP_INDUSTRY}" ]; then
    ./run_full_filibuster.sh audible
    BAD_METADATA=1 ./run_full_filibuster.sh audible bad-metadata
    ./run_full_filibuster.sh expedia
    ./run_full_filibuster.sh mailchimp
    DB_READ_ONLY=1 ./run_full_filibuster.sh mailchimp db-read-only
    ./run_full_filibuster.sh netflix
    NETFLIX_FAULTS=1 ./run_full_filibuster.sh netflix faults
fi
