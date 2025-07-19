#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../../..")
benchmark_id=$1
benchmark_category="hotelreservation"
result_tag=$2
result_path="${project_path}/results/${benchmark_category}/${benchmark_id}"
test_name=$benchmark_id

benchmark_path=${CORPUS_PATH:-"${project_path}/../../benchmarks/DeathStarBench/hotelReservation"}
benchmark_path=$(realpath "${benchmark_path}")

output_file="${result_path}/${benchmark_id}${result_tag}.log"

echo "Running ${benchmark_category} benchmark: ${benchmark_id} (${test_name})"
echo "Storing in ${output_file}"
echo "Using benchmark path: ${benchmark_path}"

# Check if the benchmark path exists
if [ ! -d "${benchmark_path}" ]; then
  echo "Error: Benchmark path ${benchmark_path} does not exist."
  exit 1
fi

PROXY_IMAGE=${PROXY_IMAGE:-"fit-proxy:latest"}
CONTROLLER_IMAGE=${CONTROLLER_IMAGE:-"fit-controller:latest"}

# Build images
cd ${benchmark_path}
if [ "${BUILD_BEFORE:-0}" == "1" ]; then
    PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml build
fi

# Start containers
if [ "${SKIP_RESTART:-0}" != "1" ]; then
  PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml up -d --force-recreate --remove-orphans

  until curl -s -o /dev/null -w "%{http_code}" http://localhost:5000/ | grep -qv '^5'; do
    echo "Waiting for services to be available..."
    sleep 1
  done

  # Wait for containers to be healthy
  sleep 5
fi

# Run tests
cd ${project_path}
mkdir -p ${result_path}
OUTPUT_TAG=$result_tag mvn clean test -Dtest=HotelReservationSuiteIT#test${test_name} | tee ${output_file}

if [ "${STOP_AFTER:-1}" == "1" ]; then
    cd ${benchmark_path}
    PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml down
    exit 0
fi
