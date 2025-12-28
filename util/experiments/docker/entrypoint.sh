#!/bin/sh
tag="${TAG:-}"
ser_tag="NO-SER${tag:+-$tag}"
repeat_count="${N:-10}"

# Start the Docker daemon in the background
dockerd-entrypoint.sh &

# Wait for the Docker daemon to be ready
until docker info >/dev/null 2>&1; do
  echo "Waiting for Docker daemon..."
  sleep 1
done

cd reynard
# make build-all
# make install


# 4. Run the experiments
echo "reynard.output.dir=/results/tests" > library/src/test/resources/junit-platform.properties
# N=${repeat_count} ./util/experiments/filibuster/run_all_filibuster_n.sh ${tag}
# N=${repeat_count} ./util/experiments/otel/run_all_otel.sh ${tag}
# N=${repeat_count} ./util/experiments/hotelreservation/run_all_n.sh ${tag}
# PROXY_RETRY_COUNT=0 N=${repeat_count} ./util/experiments/meta/run_all_meta.sh ${tag}
# PROXY_RETRY_COUNT=2 N=${repeat_count} ./util/experiments/meta/run_all_meta.sh ${tag}
# PROXY_RETRY_COUNT=4 N=${repeat_count} ./util/experiments/meta/run_all_meta.sh ${tag}
# N=${repeat_count} ./util/experiments/meta/run_all_meta.sh ${tag}

# 4. Run the experiments without SER for comparison
# 4.1 Save results in a different directory
echo "reynard.output.dir=/results/without-ser" > library/src/test/resources/junit-platform.properties

# USER_SER=false N=1 ./util/experiments/filibuster/run_all_filibuster_n.sh ${ser_tag}
# USER_SER=false N=1 ./util/experiments/otel/run_all_otel.sh ${ser_tag}
# USER_SER=false N=1 ./util/experiments/hotelreservation/run_all_n.sh ${ser_tag}
# USER_SER=false N=1 ./util/experiments/meta/run_all_meta.sh ${ser_tag}
# USER_SER=false N=1 ./util/experiments/micro/run_all_micro.sh ${ser_tag}

tail -f /dev/null