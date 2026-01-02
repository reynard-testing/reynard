#!/bin/sh

tag="${TAG:-}"
ser_tag="${tag:+$tag-}NO-SER"
repeat_count="${N:-10}"
use_docker=${USE_DOCKER:-true}
output_dir=${OUTPUT_DIR:-"./results"}

mkdir -p ${output_dir}
results_dir=$(realpath "${output_dir}")

# Note: this script assumes that Reynard and the benchmarks are already cloned.
base_path=$(dirname "$0")
base_path=$(realpath "$base_path"/../../../..)
cd ${base_path}/reynard

# With SER
envs="USE_DOCKER=${use_docker} OUT_DIR=${results_dir} TAG=${tag} N=${repeat_count}"
env ${envs} ./util/experiments/filibuster/run_all_n_docker.sh
env ${envs} ./util/experiments/hotelreservation/run_all_n_docker.sh
env ${envs} ./util/experiments/otel/run_all_n_docker.sh
env ${envs} ./util/experiments/micro/run_all_n_docker.sh
env ${envs} ./util/experiments/meta/run_all_n_docker.sh

# Without SER
envs="USE_DOCKER=${use_docker} OUT_DIR=${results_dir} TAG=${ser_tag} N=1 USE_SER=false"

env ${envs} ./util/experiments/filibuster/run_all_n_docker.sh
env ${envs} ./util/experiments/hotelreservation/run_all_n_docker.sh
env ${envs} ./util/experiments/otel/run_all_n_docker.sh
env ${envs} ./util/experiments/micro/run_all_n_docker.sh
env ${envs} ./util/experiments/meta/run_all_n_docker.sh