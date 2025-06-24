parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
project_path=$(realpath "${parent_path}/../../..")
corpus_path=${1:-"${project_path}/../../benchmarks/filibuster-corpus"}
corpus_path=$(realpath "${corpus_path}")

echo "Using corpus path: ${corpus_path}"


convert_docker_compose() {
    local service_name=$1
    cd ${project_path}/util/converter/;
    poetry run python ./converter.py --filibuster "${corpus_path}/${service_name}/docker-compose.yml"
}

# Convert all docker-compose files
convert_docker_compose "cinema-1"
convert_docker_compose "cinema-2"
convert_docker_compose "cinema-3"
convert_docker_compose "cinema-4"
convert_docker_compose "cinema-5"
convert_docker_compose "cinema-6"
convert_docker_compose "cinema-7"
convert_docker_compose "cinema-8"
convert_docker_compose "cinema-9"
convert_docker_compose "cinema-10"
convert_docker_compose "cinema-11"
convert_docker_compose "cinema-13"
convert_docker_compose "cinema-14"
convert_docker_compose "audible"
convert_docker_compose "expedia"
convert_docker_compose "mailchimp"
convert_docker_compose "netflix"
