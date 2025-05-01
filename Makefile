include .env

.PHONY: test build-controller build-proxy build-all run-test install

build-controller:
	cd ./services/controller; docker build -t fit-controller:latest .

build-proxy:
	cd ./services/proxy; docker build -t fit-proxy:latest .

build-all: build-controller build-proxy

run-test:
	cd ./lib; mvn test

install:
	mvn install -Dmaven.test.skip
