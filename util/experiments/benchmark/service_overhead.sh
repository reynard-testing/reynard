#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../../..")
benchmark_category="overhead"
result_tag=${1:+-$1}
result_path="${project_path}/results/${benchmark_category}"

echo "Running service overhead benchmark"
echo "Storing in ${result_path}"
mkdir -p ${result_path}

trap "exit" INT

controller_port=${CONTROLLER_PORT:-5050}
# controller_port=${CONTROLLER_PORT:-8081}

test_duration=${TEST_DURATION:-30s}
max_connections=${MAX_CONNECTIONS:-4}
threads=${THREADS:-4}

start_experiment() {
    local tag=${result_tag}$1

    log_dir="$result_path/$tag"
    mkdir -p "${log_dir}"

    cd ${parent_path}
    OUTPUT_DIR=$log_dir docker compose up -d --force-recreate --remove-orphans
    sleep 5
}

run_experiment() {
    local tag=${result_tag}$1
    shift
    local cmd=("$@")

    log_dir="$result_path/$tag"
    log_file="$log_dir/wrk.log"

    echo "Storing in ${log_file}"
    echo "Running: ${cmd[*]}"

    stdbuf -oL wrk -t${threads} -c${max_connections} -d${test_duration} --latency "${cmd[@]}" 2>&1 | tee "$log_file"

    # echo "Running pprof"
    # curl localhost:6060/debug/pprof/profile?seconds=50 -o "$log_dir/profile.pprof"
}

register_no_fault() {
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
  ]}' http://localhost:$controller_port/v1/faultload/register
}

register_parent_event() {
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
}' http://localhost:$controller_port/v1/proxy/report
}

register_fault() {
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
]}' http://localhost:$controller_port/v1/faultload/register
}

cleanup_experiment() {
  docker compose down
}

# Set sysctl parameters to avoid running out of ephemeral ports
sudo sysctl -w net.ipv4.ip_local_port_range="1024 65535"
sudo sysctl -w net.ipv4.tcp_fin_timeout=10
sudo sysctl -w net.ipv4.tcp_tw_reuse=1
echo "Adjusted sysctl settings for ephemeral ports"

# --- Service overhead ---
start_experiment "only_service"
run_experiment "only_service" -H 'Connection: close' http://localhost:8080/

start_experiment "only_service_keep_alive"
run_experiment "only_service_keep_alive" http://localhost:8080/

# --- Service overhead with proxy ---
start_experiment "proxy"
run_experiment "proxy" -H 'Connection: close' http://localhost:8081/

start_experiment "proxy_keep_alive"
run_experiment "proxy_keep_alive" http://localhost:8081/

# init=1
# mask
# hashbody
# headerlog
# use-cs

# --- no fault, init=1 ---
start_experiment "proxy_no_faults_init"
register_no_fault
register_parent_event
run_experiment "proxy_no_faults_init" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,init=1"


# --- No faults to inject ---
start_experiment "proxy_no_faults"
register_no_fault
register_parent_event
run_experiment "proxy_no_faults" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,fit-parent=ce086bebffb14783"

# --- With faults to inject, init ---
start_experiment "proxy_faults_init"
register_fault
run_experiment "proxy_faults_init" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,init=1"


# --- With faults to inject ---
start_experiment "proxy_faults"
register_fault
register_parent_event
run_experiment "proxy_faults" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,fit-parent=ce086bebffb14783"

# # --- cleanup ---
# cleanup_experiment