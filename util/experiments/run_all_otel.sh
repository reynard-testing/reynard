#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../..")
result_tag=${1:+-$1}

otel_demo_path=${OTEL_PATH:-"${project_path}/../../benchmarks/opentelemetry-demo-ds-fit"}
otel_demo_path=$(realpath "${otel_demo_path}")
otel_demo_path=${otel_demo_path}


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
cd ${project_path}
echo "returning to ${project_path}"
iterations=${N:-1}
for ((i=1; i<=iterations; i++)); do
    echo "Running iteration ${i} of ${iterations}"

    OUTPUT_TAG=${i} SKIP_RESTART=1 ./util/experiments/run_full_otel.sh shipping
    OUTPUT_TAG=${i} SKIP_RESTART=1 ./util/experiments/run_full_otel.sh recommendations
    OUTPUT_TAG=${i} SKIP_RESTART=1 ./util/experiments/run_full_otel.sh checkout
done

cd ${otel_demo_path}; docker compose -f docker-compose.fit.yml down
