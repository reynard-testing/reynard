.PHONY: test build-collector build-proxy build-all run-test

build-collector:
	cd ./services/collector; docker build -t fit-otel-collector:latest .

build-proxy:
	cd ./services/proxy; docker build -t fit-proxy:latest .

build-all: build-collector build-proxy

run-test:
	cd ./test/example-fit; mvn test
