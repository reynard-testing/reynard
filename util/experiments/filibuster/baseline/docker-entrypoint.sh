#!/bin/bash
tag="${TAG:-}"
dr_tag="${TAG:+no-dr-$TAG}"
dr_tag="${dr_tag:-no-dr}"
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

# 3. Run the experiments
N=${repeat_count} USE_COLOR=false ./run_experiments_n.sh ${tag}
DISABLE_DR=1 USE_COLOR=false ./run_experiments.sh ${dr_tag}