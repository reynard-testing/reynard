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
./build.sh
```

## Configuration

Set the following environment variables on the Lambda function:

| Variable          | Description                      | Example                 |
| ----------------- | -------------------------------- | ----------------------- |
| `PROXY_TARGET`    | Target service URL               | `http://service-a:5050` |
| `SERVICE_NAME`    | Name of the target service       | `service-a`             |
| `CONTROLLER_HOST` | FIT controller address           | `controller:8080`       |
| `LOG_LEVEL`       | `error`, `warn`, `info`, `debug` | `info`                  |

## Trace headers

The proxy uses W3C `traceparent`/`tracestate` headers. Include `fit=1` in `tracestate` on
outgoing requests to enable fault injection for that trace.
