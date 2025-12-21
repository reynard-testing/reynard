#!/bin/bash
tag="${TAG:-}"
repeat_count="${N:-10}"

# 1. Start the Docker daemon in the background
echo "Starting Docker daemon..."
dockerd > /var/log/docker.log 2>&1 &

# 2. Wait for the Docker daemon to be ready
# It takes a few seconds for the unix socket to be created
echo "Waiting for Docker to start..."
counter=0
while (! docker info > /dev/null 2>&1); do
    if [ $counter -gt 30 ]; then
        echo "Error: Docker daemon failed to start"
        cat /var/log/docker.log
        exit 1
    fi
    sleep 1
    counter=$((counter + 1))
done
echo "Docker daemon is ready!"

# 3. Build and install Reynard
make build-all && make install

# 4. Run the experiments
echo "reynard.output.dir=results/tests" > lib/src/test/resources/junit-platform.properties
N=${repeat_count} ./util/experiments/filibuster/run_all_filibuster_n.sh ${tag}
N=${repeat_count} ./util/experiments/otel/run_all_otel.sh ${tag}
N=${repeat_count} ./util/experiments/hotelreservation/run_all_n.sh ${tag}
PROXY_RETRY_COUNT=0 N=${repeat_count} ./util/experiments/meta/run_all_meta.sh ${tag}
PROXY_RETRY_COUNT=2 N=${repeat_count} ./util/experiments/meta/run_all_meta.sh ${tag}
PROXY_RETRY_COUNT=4 N=${repeat_count} ./util/experiments/meta/run_all_meta.sh ${tag}
N=${repeat_count} ./util/experiments/meta/run_all_meta.sh ${tag}

# 4. Run the experiments without SER for comparison
echo "reynard.output.dir=results/without-ser" > lib/src/test/resources/junit-platform.properties
USER_SER=false N=1 ./util/experiments/filibuster/run_all_filibuster_n.sh ${tag}
USER_SER=false N=1 ./util/experiments/otel/run_all_otel.sh ${tag}
USER_SER=false N=1 ./util/experiments/hotelreservation/run_all_n.sh ${tag}
USER_SER=false N=1 ./util/experiments/meta/run_all_meta.sh ${tag}
USER_SER=false N=1 ./util/experiments/meta/run_all_meta.sh ${tag}
