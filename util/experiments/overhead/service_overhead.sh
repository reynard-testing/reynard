#!/bin/bash

# ------------------------------------------------------------------
# This script runs all service overhead experiments.
#
# Usage: ./service_overhead.sh [result_tag]
# Env:
#   TEST: Optional, run only a specific test case.
#   TEST_DURATION: Optional, duration for wrk load test (default: 1m).
#   MAX_CONNECTIONS: Optional, max connections for wrk (default: 4).
#   THREADS: Optional, number of threads for wrk (default: 4).
# ------------------------------------------------------------------

result_tag=${TAG:-"default"}
results_dir=${OUT_DIR:-"./results"}
use_docker=${USE_DOCKER:-true}

echo "Running service overhead benchmark"
echo "Storing in ${results_dir}"
mkdir -p ${results_dir}

trap "exit" INT

# Path setup
experiment_dir=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
reynard_path=$(realpath "${experiment_dir}/../../..")

results_dir=$(realpath "${results_dir}")
log_dir="${results_dir}/overhead/logs/"

# Build wrk runner image
docker network create reynard_benchmark_net 2>/dev/null || true
docker build -t wrk-runner:latest -f ${experiment_dir}/wrk-runner/Dockerfile ${experiment_dir}/wrk-runner


start_experiment() {
  local use_dummy=$1
  if [ -z "$use_dummy" ]; then
    host=controller:5000
  else
    host=controller-dummy:8080
  fi
  echo "Starting experiment with host: $host"
  cd ${experiment_dir}
  CONTROLLER_HOST=$host docker compose up -d --force-recreate --remove-orphans

  echo "Sleeping 30 seconds to allow services to settle..."
  sleep 30
}

run_experiment() {
  local tag=$1
  shift
  local cmd=("$@")

  test_duration=${TEST_DURATION:-1m}
  max_connections=${MAX_CONNECTIONS:-4}
  threads=${THREADS:-4}

  logging_dir="$log_dir/$tag/${result_tag}"
  mkdir -p "$logging_dir"
  log_file="$logging_dir/wrk.log"

  echo "Storing in ${log_file}"
  echo "Running: ${cmd[*]}"

  docker run \
    --rm \
    --network="reynard_benchmark_net" \
    --sysctl net.ipv4.tcp_synack_retries=2 \
    --sysctl net.ipv4.ip_local_port_range="1024 65535" \
    --sysctl net.ipv4.tcp_fin_timeout=15 \
    --sysctl net.ipv4.tcp_tw_reuse=1 \
    wrk-runner:latest \
    wrk -t${threads} -c${max_connections} -d${test_duration} -s ./random_payload.lua --latency "${cmd[@]}" | tee ${log_file}
}

register_payload() {
  local file=$1

  file_abs=$(realpath "$experiment_dir/$file")
  curl -X POST -H "Content-Type: application/json" --data-binary @$file_abs http://localhost:5050/v1/faultload/register
}

report_parent_event() {
  curl -X POST -H "Content-Type: application/json"  --data-binary @payloads/report_event.json http://localhost:5050/v1/proxy/report
}

cleanup_experiment() {
  docker compose down
}

# --- 01. Direct to service, omitting proxy ---
if [ -z "$TEST" ] || [ "$TEST" = "direct_to_service" ]; then
  start_experiment
  run_experiment "direct_to_service" http://target:8080/
fi

# --- 02. Direct to proxy, no headers ---
if [ -z "$TEST" ] || [ "$TEST" = "no_headers" ]; then
  start_experiment
  run_experiment "no_headers" http://proxy:8080/
fi


# ==== Unknown trace ====
# --- 03. Unknown trace ---
if [ -z "$TEST" ] || [ "$TEST" = "unknown_trace" ]; then
  start_experiment
  register_payload payloads/no_matching_faults.json 
  run_experiment "unknown_trace" http://proxy:8080/ -H "traceparent: 00-00cbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,fit-parent=ce086bebffb14783"
fi


# ==== No matching faults to inject ====
# --- 04. No faults to inject ---
if [ -z "$TEST" ] || [ "$TEST" = "no_matching_faults" ]; then
  start_experiment
  register_payload payloads/no_matching_faults.json
  report_parent_event
  run_experiment "no_matching_faults" http://proxy:8080/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,fit-parent=ce086bebffb14783"
fi

# --- no fault, init=1 ---
if [ -z "$TEST" ] || [ "$TEST" = "no_matching_faults_init" ]; then
  start_experiment
  register_payload payloads/no_matching_faults.json
  report_parent_event
  run_experiment "no_matching_faults_init" http://proxy:8080/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,init=1"
fi

# --- 05. No faults to inject, dummy ---
if [ -z "$TEST" ] || [ "$TEST" = "no_matching_faults_mocked" ]; then
  start_experiment dummy
  run_experiment "no_matching_faults_mocked" http://proxy:8080/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1"
fi

# --- No faults to inject, dummy ---
if [ -z "$TEST" ] || [ "$TEST" = "no_matching_faults_mocked_init" ]; then
  start_experiment dummy
  run_experiment "no_matching_faults_mocked_init" http://proxy:8080/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,init=1"
fi



# ==== With faults to inject ====
# --- 06. With faults to inject ---
if [ -z "$TEST" ] || [ "$TEST" = "matching_faults" ]; then
  start_experiment
  register_payload payloads/matching_fault.json
  report_parent_event
  run_experiment "matching_faults" http://proxy:8080/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,fit-parent=ce086bebffb14783"
fi



# ==== With numerous faults to inject ====
# --- 07. With faults to inject ---
if [ -z "$TEST" ] || [ "$TEST" = "many_faults" ]; then
  start_experiment
  register_payload payloads/large_faultload.json
  report_parent_event
  run_experiment "many_faults" http://proxy:8080/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,fit-parent=ce086bebffb14783"
fi

# ==== No matching faults, different durations ====
if [ -z "$TEST" ] || [ "$TEST" = "no_matching_faults_t15" ]; then
  export TEST_DURATION=15s
  start_experiment
  register_payload payloads/no_matching_faults.json
  report_parent_event
  run_experiment "no_matching_faults_t15" http://proxy:8080/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,fit-parent=ce086bebffb14783"
fi

if [ -z "$TEST" ] || [ "$TEST" = "no_matching_faults_t30" ]; then
  export TEST_DURATION=30s
  start_experiment
  register_payload payloads/no_matching_faults.json
  report_parent_event
  run_experiment "no_matching_faults_t30" http://proxy:8080/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,fit-parent=ce086bebffb14783"
fi

if [ -z "$TEST" ] || [ "$TEST" = "no_matching_faults_t120" ]; then
  export TEST_DURATION=2m
  start_experiment
  register_payload payloads/no_matching_faults.json
  report_parent_event
  run_experiment "no_matching_faults_t120" http://proxy:8080/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,fit-parent=ce086bebffb14783"
fi


# # --- cleanup ---
cleanup_experiment
