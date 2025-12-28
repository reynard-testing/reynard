#!/bin/bash
# ------------------------------------------------------------------
# This script runs all otel experiments for the astronomy-shop benchmark.
#
# Usage: ./run_all_otel.sh [result_tag]
# Env:
#   OTEL_PATH: Optional, path to the otel benchmark directory.
#   N: Optional, number of iterations to run (default: 10).
# ------------------------------------------------------------------

parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../../..")
result_tag=${1:+-$1}

otel_demo_path=${OTEL_PATH:-"${project_path}/../benchmarks/astronomy-shop"}
otel_demo_path=$(realpath "${otel_demo_path}")
otel_demo_path=${otel_demo_path}

iterations=${N:-10}

echo "Storing in ${output_file}"
echo "Using corpus path: ${otel_demo_path}"

# Check if the corpus path exists
if [ ! -d "${otel_demo_path}" ]; then
  echo "Error: Corpus path ${otel_demo_path} does not exist."
  exit 1
fi

# Build images
cd ${otel_demo_path}; docker compose -f docker-compose.fit.yml build
# # Start containers
cd ${otel_demo_path}; make start-fit


# Wait for containers to be healthy
# Wait for the health check to pass
echo "Waiting for http://localhost:8080/ to be available..."
until curl -s http://localhost:8080/ > /dev/null; do
  echo "Service not available yet. Retrying in 5 seconds..."
  sleep 5
done
echo "Service is available."


trap "exit" INT
cd ${project_path}/util/experiments/otel/
echo "returning to ${project_path}"

for ((i=1; i<=iterations; i++)); do
    echo "Running iteration ${i} of ${iterations}"

    OUTPUT_TAG=${i}${result_tag} SKIP_RESTART=1 ./run_full_otel.sh shipping ${i}${result_tag}
    OUTPUT_TAG=${i}${result_tag} SKIP_RESTART=1 ./run_full_otel.sh recommendations ${i}${result_tag}
    OUTPUT_TAG=${i}${result_tag} SKIP_RESTART=1 ./run_full_otel.sh recommendationsWithPruner ${i}${result_tag}
    OUTPUT_TAG=${i}${result_tag} SKIP_RESTART=1 ./run_full_otel.sh checkout ${i}${result_tag}
    OUTPUT_TAG=${i}${result_tag} SKIP_RESTART=1 ./run_full_otel.sh checkoutWithCs ${i}${result_tag}
done

cd ${otel_demo_path}; docker compose -f docker-compose.fit.yml down
