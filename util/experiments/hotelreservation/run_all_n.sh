#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}")
project_root_path=$(realpath "${parent_path}/../../..")

benchmark_path=${CORPUS_PATH:-"${project_root_path}/../../benchmarks/DeathStarBench/hotelReservation"}
benchmark_path=$(realpath "${benchmark_path}")

result_tag=${1:+-$1}
iterations=${N:-10}

export STOP_AFTER=1
export BUILD_BEFORE=${BUILD_BEFORE:-0}


run_n_benchmark() {
    local benchmark_id=$1

    for ((i=1; i<=iterations; i++)); do
        iteration_tag="${result_tag}${env_tag}${i}"

        echo "Running ${benchmark_id} benchmark (iteration $i of $iterations) (tag: $iteration_tag)"
        OUTPUT_TAG="${iteration_tag}" SKIP_RESTART=1 BUILD_BEFORE=0 STOP_AFTER=0 ./run_full_test.sh ${benchmark_id} ${iteration_tag}
    done
}

cd ${benchmark_path}
trap "exit" INT

PROXY_IMAGE=${PROXY_IMAGE:-"fit-proxy:latest"}
CONTROLLER_IMAGE=${CONTROLLER_IMAGE:-"fit-controller:latest"}

if [ "${BUILD_BEFORE:-0}" == "1" ]; then
    PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml build
fi

# Start containers
PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml up -d --force-recreate --remove-orphans

until curl -s -o /dev/null -w "%{http_code}" http://localhost:5000/ | grep -qv '^5'; do
echo "Waiting for services to be available..."
sleep 1
done

# Wait for containers to be healthy
sleep 5

cd ${project_path}
run_n_benchmark "SearchHotels"
run_n_benchmark "Recommend"
run_n_benchmark "Reserve"
run_n_benchmark "Login"

cd ${benchmark_path}
PROXY_IMAGE=${PROXY_IMAGE} CONTROLLER_IMAGE=${CONTROLLER_IMAGE} docker compose -f docker-compose.fit.yml down
exit 0