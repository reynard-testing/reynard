# Instrumentation Proxy

The proxy exposes two ports: one that serves as a reverse proxy, and one that serves as an API to control the proxy.

## Control Endpoints
The proxy exposes a control API server used by the controller:

| URL | Method | Description |
| --- | ------ | ----------- |
| `/v1/faultload/register` | `POST`  | Register a `trace_id` at all proxies, to track it.
| `/v1/faultload/unregister` | `POST`  | Remove a `trace_id`.

For both endpoints, the proxy will check which faults are relevant to the proxy, and store only those.

## Configuration

The environment can be configured using environment variables:

| Key | Default | Description |
| --- | ------ | ----------- |
| `PROXY_HOST` | `""`  | The host of how the proxy runs (i.e., `"localhost:5000"`).
| `PROXY_TARGET` | `""`  | The target of proxied requests (i.e., `"service-a:5050"`)
| `SERVICE_NAME` | `""`  | The short unique name of the target service. As fallback, the target is used to define a name.
| `CONTROL_PORT` | `""`  | The port that exposes the control API. Keep in mind to set it to a different port than the `PROXY_HOST`. By default its the `PROXY_HOST` port + 1.
| `LOG_LEVEL` | `info`  | Log level:  `error`, `warn`, `info` or `debug`.
| `USE_OTEL` | `false`  | Whether to use OTel internally.

If `USE_OTEL` is set to `true`, you can configure [OpenTelemetry configurations](https://opentelemetry.io/docs/specs/otel/configuration/sdk-environment-variables/) in the environment too.