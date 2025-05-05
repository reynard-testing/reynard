include .env

.PHONY: test build-controller build-proxy build-all run-test install

build-controller:
	cd ./services/; docker build -t fit-controller:latest -f controller/Dockerfile .

build-proxy:
	cd ./services/; docker build -t fit-proxy:latest -f proxy/Dockerfile .

build-all: build-controller build-proxy

run-test:
	cd ./lib; mvn test

install:
	mvn install -Dmaven.test.skip
