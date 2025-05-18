#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../../..")
benchmark_category="overhead"
result_tag=${1:-default}
result_path="${project_path}/results/${benchmark_category}"

echo "Running service overhead benchmark"
echo "Storing in ${result_path}"
mkdir -p ${result_path}

trap "exit" INT

controller_port=${CONTROLLER_PORT:-5050}
# controller_port=${CONTROLLER_PORT:-8081}

test_duration=${TEST_DURATION:-1m}
max_connections=${MAX_CONNECTIONS:-4}
threads=${THREADS:-4}

start_experiment() {
    if [ -z "$1" ]; then
      host=controller:5000
    else
      host=controller-dummy:8080
    fi
    echo "Starting experiment with host: $host"
    cd ${parent_path}
    CONTROLLER_HOST=$host docker compose up -d --force-recreate --remove-orphans
    sleep 5
}

run_experiment() {
    local tag=$1
    shift
    local cmd=("$@")

    log_dir="$result_path/$tag/${result_tag}"
    mkdir -p "$log_dir"
    log_file="$log_dir/wrk.log"

    echo "Storing in ${log_file}"
    echo "Running: ${cmd[*]}"

    stdbuf -oL wrk -t${threads} -c${max_connections} -d${test_duration} --latency "${cmd[@]}" 2>&1 | tee "$log_file"
}

register_no_fault() {
  local port=${1:-$controller_port}
  curl -X POST -H "Content-Type: application/json" -d '{"trace_id":"efcbf3a8ae78f65a35bf05ddcc8419e8", "faults":[{
    "uid": {
      "stack": [
        {
          "destination": "target",
          "signature": "",
          "payload": "",
          "call_stack": {},
          "count": 0
        }
      ]
    },
    "mode": { "type": "HTTP_ERROR", "args": ["500"] }
  }
  ]}' http://localhost:$port/v1/faultload/register
}

register_parent_event() {
  local port=${1:-$controller_port}
  curl -X POST -H "Content-Type: application/json" -d '{
  "trace_id":"efcbf3a8ae78f65a35bf05ddcc8419e8",
  "span_id": "ce086bebffb14783",
  "uid": {
    "stack": []
  },
  "is_initial": true,
  "injected_fault": null,
  "response": {
    "status": 200,
    "body": "OK",
    "duration_ms": 1
  },
  "concurrent_to": []
}' http://localhost:$port/v1/proxy/report
}

register_fault() {
  local port=${1:-$controller_port}
  curl -X POST -H "Content-Type: application/json" -d '{"trace_id":"efcbf3a8ae78f65a35bf05ddcc8419e8", "faults":[{
    "uid": {
      "stack": [
        {
          "destination": "target",
          "signature": "*",
          "payload": "*",
          "call_stack": {},
          "count": -1
        }
      ]
    },
    "mode": { "type": "HTTP_ERROR", "args": ["500"] }
  }
  ]}' http://localhost:$port/v1/faultload/register
}

cleanup_experiment() {
  docker compose down
}

# Set sysctl parameters to avoid running out of ephemeral ports
sudo sysctl -w net.ipv4.tcp_synack_retries=2
sudo sysctl -w net.ipv4.ip_local_port_range="1024 65535"
sudo sysctl -w net.ipv4.tcp_fin_timeout=15
sudo sysctl -w net.ipv4.tcp_tw_reuse=1
echo "Adjusted sysctl settings for ephemeral ports"


# --- Service overhead ---
if [ -z "$TEST" ] || [ "$TEST" = "only_service" ]; then
  start_experiment
  # run_experiment "only_service" -H 'Connection: close' http://localhost:8080/
  run_experiment "only_service_keep_alive" http://localhost:8080/
fi

# --- Service overhead with proxy ---
if [ -z "$TEST" ] || [ "$TEST" = "proxy" ]; then
  start_experiment
  # run_experiment "proxy" -H 'Connection: close' http://localhost:8081/
  run_experiment "proxy_keep_alive" http://localhost:8081/
fi

if [ -z "$TEST" ] || [ "$TEST" = "proxy_dummy" ]; then
  start_experiment dummy
  run_experiment "proxy_dummy" http://localhost:8081/
fi

# init=1
# mask
# hashbody
# headerlog
# use-cs

# ==== Unknown trace ====
# --- unkown trace ---
if [ -z "$TEST" ] || [ "$TEST" = "proxy_other_trace" ]; then
  start_experiment
  register_fault
  run_experiment "proxy_other_trace" http://localhost:8081/ -H "traceparent: 00-00cbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,fit-parent=ce086bebffb14783"
fi


# ==== No matching faults to inject ====
# --- No faults to inject ---
if [ -z "$TEST" ] || [ "$TEST" = "proxy_no_faults" ]; then
  start_experiment
  register_no_fault
  register_parent_event
  run_experiment "proxy_no_faults" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,fit-parent=ce086bebffb14783"
fi

# --- no fault, init=1 ---
if [ -z "$TEST" ] || [ "$TEST" = "proxy_no_faults_init" ]; then
  start_experiment
  register_no_fault
  register_parent_event
  run_experiment "proxy_no_faults_init" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,init=1"
fi

# --- No faults to inject, dummy ---
if [ -z "$TEST" ] || [ "$TEST" = "proxy_dummy_no_faults" ]; then
  start_experiment dummy
  register_no_fault 8050
  run_experiment "proxy_dummy_no_faults" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1"
fi

# --- No faults to inject, dummy ---
if [ -z "$TEST" ] || [ "$TEST" = "proxy_dummy_no_faults_init" ]; then
  start_experiment dummy
  register_no_fault 8050
  run_experiment "proxy_dummy_no_faults_init" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,init=1"
fi



# ==== With faults to inject ====
# --- With faults to inject ---
if [ -z "$TEST" ] || [ "$TEST" = "proxy_faults" ]; then
  start_experiment
  register_fault
  register_parent_event
  run_experiment "proxy_faults" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,fit-parent=ce086bebffb14783"
fi

# --- With faults to inject, init ---
if [ -z "$TEST" ] || [ "$TEST" = "proxy_faults_init" ]; then
  start_experiment
  register_fault
  run_experiment "proxy_faults_init" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,init=1"
fi

# --- With faults to inject, dummy ---
if [ -z "$TEST" ] || [ "$TEST" = "proxy_dummy_faults" ]; then
  start_experiment dummy
  register_fault 8050
  register_parent_event 8050
  run_experiment "proxy_dummy_faults" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,fit-parent=ce086bebffb14783"
fi

# --- With faults to inject, dummy ---
if [ -z "$TEST" ] || [ "$TEST" = "proxy_dummy_faults_init" ]; then
  start_experiment dummy
  register_fault 8050
  register_parent_event 8050
  run_experiment "proxy_dummy_faults_init" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,init=1"
fi


# # --- cleanup ---
# cleanup_experiment