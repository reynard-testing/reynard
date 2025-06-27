base_tag=${1:-""}
iterations=${N:-10}

trap "exit" INT
for ((i=1; i<=iterations; i++)); do
    OUTPUT_TAG=${base_tag}${i} ./run_full_micro.sh ResiliencePatternsIT testA
    OUTPUT_TAG=${base_tag}${i} ./run_full_micro.sh ResiliencePatternsIT testCs
    OUTPUT_TAG=${base_tag}${i} ./run_full_micro.sh ResiliencePatternsIT testOpt
    OUTPUT_TAG=${base_tag}${i} ./run_full_micro.sh ResiliencePatternsIT testCsOpt
done
