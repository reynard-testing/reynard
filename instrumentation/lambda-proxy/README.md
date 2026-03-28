# FIT Lambda Proxy

A custom Lambda runtime that runs the Fault Injection Testing (FIT) proxy as a Lambda function,
enabling the same fault injection framework used in Docker/Kubernetes deployments to cover
Lambda-based microservices.

## How it works

The binary acts as a Lambda custom runtime (`bootstrap`). It registers a handler via the Lambda
Runtime API (`lambda.Start`) that is invoked for each incoming request.

For each invocation, the handler extracts the trace context (W3C `traceparent` or X-Ray), checks
whether FIT is enabled for that trace, consults the FIT controller for fault decisions, and then
forwards the request to the configured `PROXY_TARGET`. Faults (delays, errors, omissions) are
injected as configured, and the span is reported back to the controller after the request
completes.

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
