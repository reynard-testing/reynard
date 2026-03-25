# FIT Lambda Proxy

A custom Lambda runtime that runs the Fault Injection Testing (FIT) proxy as a Lambda function,
enabling the same fault injection framework used in Docker/Kubernetes deployments to cover
Lambda-based microservices.

## How it works

The binary acts as a Lambda custom runtime (`bootstrap`). On startup it launches the FIT reverse
proxy HTTP server as a goroutine, then enters the Lambda Runtime API event loop. For each Lambda
invocation, the runtime API is polled, the invocation is acknowledged, and the proxy continues
running between invocations.

Outgoing HTTP calls from a Lambda function that routes traffic through this proxy are intercepted,
consulted against the FIT controller for fault decisions, and faults (delays, errors, omissions)
are injected as configured.

## Build

```bash
make build-lambda-extension
```

This produces the `fit-lambda-extension:latest` Docker image. Extract the `bootstrap` binary to
package it for Lambda:

```bash
# Extract binary from image
docker create --name fit-lambda-tmp fit-lambda-extension:latest
docker cp fit-lambda-tmp:/var/task/bootstrap ./bootstrap
docker rm fit-lambda-tmp

# Zip for deployment (Lambda expects bootstrap at the root of the zip)
zip fit-lambda-proxy.zip bootstrap
```

## Deploy

1. **Create or update the Lambda function** using the `provided.al2` (or `provided.al2023`) runtime:
   ```bash
   aws lambda create-function \
     --function-name fit-proxy \
     --runtime provided.al2 \
     --handler bootstrap \
     --zip-file fileb://fit-lambda-proxy.zip \
     --role <execution-role-arn>
   ```

2. **Update an existing function**:
   ```bash
   aws lambda update-function-code \
     --function-name fit-proxy \
     --zip-file fileb://fit-lambda-proxy.zip
   ```

## Configuration

Set the following environment variables on the Lambda function:

| Variable | Description | Example |
|---|---|---|
| `PROXY_HOST` | Local address the proxy listens on | `127.0.0.1:2345` |
| `PROXY_TARGET` | Target service URL | `http://service-a:5050` |
| `SERVICE_NAME` | Name of the target service | `service-a` |
| `CONTROLLER_HOST` | FIT controller address | `controller:8080` |
| `LOG_LEVEL` | `error`, `warn`, `info`, `debug` | `info` |

## Lambda function changes

Route outgoing HTTP calls through the local proxy port instead of calling the target directly:

```python
# Before
response = requests.get("http://service-a:5050/api/data")

# After
response = requests.get("http://127.0.0.1:2345/api/data")
```

Or set `HTTP_PROXY` so HTTP clients auto-route through the proxy:

```bash
HTTP_PROXY=http://127.0.0.1:2345
```

## Trace headers

The proxy uses W3C `traceparent`/`tracestate` headers. Include `fit=1` in `tracestate` on
outgoing requests to enable fault injection for that trace.
