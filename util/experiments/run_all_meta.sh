base_tag=${1:-""}
iterations=${N:-1}

trap "exit" INT
for ((i=1; i<=iterations; i++)); do
    OUTPUT_TAG=${base_tag}${i} ./util/experiments/run_full_meta.sh register 
done
