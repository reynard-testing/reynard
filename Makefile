.PHONY: test build-orchestrator build-proxy build-all run-test

build-orchestrator:
	cd ./services/orchestrator; docker build -t fit-otel-orchestrator:latest .

build-proxy:
	cd ./services/proxy; docker build -t fit-proxy:latest .

build-all: build-orchestrator build-proxy

run-test:
	cd ./lib; mvn test
