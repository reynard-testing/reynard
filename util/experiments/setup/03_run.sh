#!/bin/sh
tag="${TAG:-}"
ser_tag="${tag:+$tag-}NO-SER"
repeat_count="${N:-10}"

mkdir -p results
results_dir=$(realpath results)

cd reynard

# With SER
# OUT_DIR=${results_dir} TAG=${tag} N=${repeat_count} ./util/experiments/hotelreservation/run_all_n_docker.sh
# OUT_DIR=${results_dir} TAG=${tag} N=${repeat_count} ./util/experiments/otel/run_all_n_docker.sh
OUT_DIR=${results_dir} TAG=${tag} N=${repeat_count} ./util/experiments/micro/run_all_n_docker.sh

# Without SER
# OUT_DIR=${results_dir} USE_SER=false TAG=${ser_tag} N=1 ./util/experiments/hotelreservation/run_all_n_docker.sh
# OUT_DIR=${results_dir} USE_SER=false TAG=${ser_tag} N=1 ./util/experiments/otel/run_all_n_docker.sh
OUT_DIR=${results_dir} USE_SER=false TAG=${ser_tag} N=1 ./util/experiments/micro/run_all_n_docker.sh