#!/bin/bash
# Note: this script assumes that Reynard and the benchmarks are already cloned.
base_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
base_path=$(realpath "$base_path"/../../../..)
trap "exit" INT

cd ${base_path}/reynard; make build-all

# Most of the docker compose files use these environment variables, so provide them
export PROXY_IMAGE=${PROXY_IMAGE:-fit-proxy:latest}
export CONTROLLER_IMAGE=${CONTROLLER_IMAGE:-fit-controller:latest}

# Astronomy shop
cd ${base_path}/benchmarks/astronomy-shop; (docker compose -f docker-compose.fit.yml build)

# DeathStarBench hotelReservation
cd ${base_path}/benchmarks/DeathStarBench/hotelReservation; (docker compose -f docker-compose.fit.yml build)

# Filibuster corpus
cd ./benchmarks/filibuster-corpus/cinema-1; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-2; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-3; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-4; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-5; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-6; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-7; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/cinema-8; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/audible; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/netflix; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/expedia; (docker compose -f docker-compose.fit.yml build)
cd ./benchmarks/filibuster-corpus/mailchimp; (docker compose -f docker-compose.fit.yml build)