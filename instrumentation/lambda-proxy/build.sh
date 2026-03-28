#!/bin/bash

parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
reynard_path=$(realpath "${parent_path}/../..")

# Extract binary from image
cd ${reynard_path}/instrumentation/
docker build -f lambda-proxy/Dockerfile -o type=local,dest=./lambda-proxy/lambda/ .

# # Zip for deployment (Lambda expects bootstrap at the root of the zip)
zip -j ./lambda-proxy/lambda/reynard-lambda-proxy.zip ./lambda-proxy/lambda/bootstrap
