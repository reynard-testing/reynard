
iterations=${N:-1}
for ((i=1; i<=iterations; i++)); do
    OUTPUT_TAG=${i} ./util/experiments/run_full_meta.sh register
done