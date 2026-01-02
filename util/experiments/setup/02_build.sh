#!/bin/sh
# Note: this script assumes that Reynard and the benchmarks are already cloned.
base_path=$(dirname "$0")
base_path=$(realpath "$base_path"/../../..)
trap "exit" INT

cd ${base_path}/reynard; (make build-all && make install)

export PROXY_IMAGE=${PROXY_IMAGE}
export CONTROLLER_IMAGE=${CONTROLLER_IMAGE}

cd ${base_path}/benchmarks/astronomy-shop; (docker compose -f docker-compose.fit.yml build)
cd ${base_path}/benchmarks/DeathStarBench/hotelReservation; (docker compose -f docker-compose.fit.yml build)

for service in cinema-{1..8} audible netflix expedia mailchimp; do
    cd ${base_path}/benchmarks/filibuster-corpus/${service}; \
    (docker compose -f docker-compose.fit.yml build)
done
