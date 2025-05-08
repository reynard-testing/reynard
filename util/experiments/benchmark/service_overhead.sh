#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../../..")
benchmark_category="overhead-benchmark"
benchmark_id="service"
result_tag=${1:+-$1}
result_path="${project_path}/results/${benchmark_category}/${benchmark_id}"

echo "Running service overhead benchmark"
echo "Storing in ${result_path}"
mkdir -p ${result_path}

trap "exit" INT

test_duration=${TEST_DURATION:-30s}
max_connections=${MAX_CONNECTIONS:-20}
threads=${THREADS:-4}

cd ${parent_path}
docker compose up -d --force-recreate --remove-orphans
sleep 5

sleep 5
wrk -t${threads} -c${max_connections} -d${test_duration} --latency -H "Connection: close" http://localhost:8080/ 2>&1 | tee ${result_path}/wrk${result_tag}.service.log

sleep 5
wrk -t${threads} -c${max_connections} -d${test_duration} --latency -H "Connection: close" http://localhost:8081/ 2>&1 | tee ${result_path}/wrk${result_tag}.proxy.log


# --- No faults to inject ---
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
]}' http://localhost:5050/v1/faultload/register

# init=1
# mask
# hashbody
# headerlog
# use-cs

sleep 5
wrk -t${threads} -c${max_connections} -d${test_duration} --latency -H "Connection: close" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1" 2>&1 | tee ${result_path}/wrk${result_tag}.proxy-no-f.log

sleep 5
wrk -t${threads} -c${max_connections} -d${test_duration} --latency -H "Connection: close" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,init=1" 2>&1 | tee ${result_path}/wrk${result_tag}.proxy-no-f-init.log

curl -X POST -H "Content-Type: application/json" -d '{"trace_id":"efcbf3a8ae78f65a35bf05ddcc8419e8"}' http://localhost:5050/v1/faultload/unregister

# --- With faults to inject ---
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
]}' http://localhost:5050/v1/faultload/register

sleep 5
wrk -t${threads} -c${max_connections} -d${test_duration} --latency -H "Connection: close" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1" 2>&1 | tee ${result_path}/wrk${result_tag}.proxy-f.log

sleep 5
wrk -t${threads} -c${max_connections} -d${test_duration} --latency -H "Connection: close" http://localhost:8081/ -H "traceparent: 00-efcbf3a8ae78f65a35bf05ddcc8419e8-0000000000000001-01" -H "tracestate: fit=1,init=1" 2>&1 | tee ${result_path}/wrk${result_tag}.proxy-f-init.log


curl -X POST -H "Content-Type: application/json" -d '{"trace_id":"efcbf3a8ae78f65a35bf05ddcc8419e8"}' http://localhost:5050/v1/faultload/unregister


# docker compose down