.PHONY: test build-controller build-proxy build-all run-test install

build-controller:
	cd ./instrumentation/; docker build -t fit-controller:latest -f controller/Dockerfile .

build-proxy:
	cd ./instrumentation/; docker build -t fit-proxy:latest -f proxy/Dockerfile .

build-library:
	docker build -t fit-library:latest .

build-library-dind:
	docker build -t fit-library-dind:latest -f Dockerfile.dind .

build-all: build-controller build-proxy build-library build-library-dind

run-test:
	cd ./library; mvn test

install:
	mvn install -Dmaven.test.skip
