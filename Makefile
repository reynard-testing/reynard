.PHONY: test build-controller build-proxy build-lambda-layer build-all run-test install

build-controller:
	cd ./instrumentation/; docker build -t fit-controller:latest -f controller/Dockerfile .

build-proxy:
	cd ./instrumentation/; docker build -t fit-proxy:latest -f proxy/Dockerfile .

# Exports the proxy as a Lambda extension layer to instrumentation/proxy/lambda-layer/
build-lambda-layer:
	cd ./instrumentation/; docker build --platform linux/amd64 --target lambda-layer -o type=local,dest=./proxy/lambda-layer/ -f proxy/Dockerfile .

build-library:
	docker build -t fit-library:latest .

build-library-dind:
	docker build -t fit-library-dind:latest -f Dockerfile.dind .

build-all: build-controller build-proxy build-library build-library-dind

run-test:
	cd ./integrations/junit; mvn test

install:
	mvn install -Dmaven.test.skip
