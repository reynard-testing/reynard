# Instrumentation Proxy

The proxy is stateless. For each request with `fit=1` in its `tracestate`, it asks the controller for the request's uid and the fault to inject (if any). Requests of traces that are not registered at the controller are forwarded untouched.

## Configuration

The environment can be configured using environment variables:

| Key | Default | Description |
| --- | ------ | ----------- |
| `PROXY_HOST` | `""`  | The host of how the proxy runs (i.e., `"localhost:5000"`).
| `PROXY_TARGET` | `""`  | The target of proxied requests (i.e., `"service-a:5050"`)
| `SERVICE_NAME` | `""`  | The short unique name of the target service. As fallback, the target is used to define a name.
| `CONTROLLER_HOST` | `""`  | The address of the controller (i.e., `"controller:5000"`).
| `LOG_LEVEL` | `info`  | Log level:  `error`, `warn`, `info` or `debug`.
| `USE_OTEL` | `false`  | Whether to use OTel internally.

If `USE_OTEL` is set to `true`, you can configure [OpenTelemetry configurations](https://opentelemetry.io/docs/specs/otel/configuration/sdk-environment-variables/) in the environment too.