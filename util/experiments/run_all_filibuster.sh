#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}")

export STOP_AFTER=1
export BUILD_BEFORE=1

cd ${project_path}
trap "exit" INT

./run_full_filibuster.sh cinema-1
./run_full_filibuster.sh cinema-2
./run_full_filibuster.sh cinema-3
./run_full_filibuster.sh cinema-4
./run_full_filibuster.sh cinema-5
./run_full_filibuster.sh cinema-6
./run_full_filibuster.sh cinema-7
./run_full_filibuster.sh cinema-8

./run_full_filibuster.sh audible
./run_full_filibuster.sh expedia
./run_full_filibuster.sh mailchimp
./run_full_filibuster.sh netflix
