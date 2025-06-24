#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../..")
benchmark_id=$1
benchmark_category="otel-demo"
result_tag=${2:+-$2}
result_path="${project_path}/results/${benchmark_category}/${benchmark_id}"
output_file="${result_path}/${benchmark_id}${result_tag}.log"

otel_demo_path=${OTEL_PATH:-"${project_path}/../../benchmarks/opentelemetry-demo-ds-fit"}
otel_demo_path=$(realpath "${otel_demo_path}")
otel_demo_path=${otel_demo_path}

test_name=$(echo "$benchmark_id" | sed -E 's/(^|-)([a-z])/\U\2/g' | tr -d '-')

echo "Running ${benchmark_category} benchmark: ${benchmark_id} (${test_name})"
echo "Storing in ${output_file}"
echo "Using corpus path: ${otel_demo_path}"

# Check if the corpus path exists
if [ ! -d "${otel_demo_path}" ]; then
  echo "Error: Corpus path ${otel_demo_path} does not exist."
  exit 1
fi

# Build images
cd ${otel_demo_path}
if [ -n "${BUILD_BEFORE}" ]; then
    docker compose -f docker-compose.fit.yml build
fi

# Start containers
if [ -z "${SKIP_RESTART}" ]; then
  make start-fit
else
  echo "Skipping stack startup as SKIP_RESTART is set."
fi

# Wait for containers to be healthy
# Wait for the health check to pass
echo "Waiting for http://localhost:8080/ to be available..."
until curl -s http://localhost:8080/ > /dev/null; do
  echo "Service not available yet. Retrying in 5 seconds..."
  sleep 5
done
echo "Service is available."

# Run tests
cd ${project_path}
mkdir -p ${result_path}
mvn clean test -Dtest=OTELSuiteIT#test${test_name} | tee ${output_file}

if [ -n "${STOP_AFTER}" ]; then
    cd ${otel_demo_path}
    docker compose -f docker-compose.fit.yml down
    exit 0
fi
