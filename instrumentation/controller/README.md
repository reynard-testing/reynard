# Instrumentation controller

## Endpoints

The controller is an API server that has the following endpoints:

### Test library endpoints

| URL                        | Method | Description                                          |
| -------------------------- | ------ | ---------------------------------------------------- |
| `/v1/faultload/register`   | `POST` | Register a `trace_id` at all proxies, to track it.   |
| `/v1/trace/{trace_id}`     | `GET`  | Get the list of reports for a registered `trace_id`. |
| `/v1/faultload/unregister` | `POST` | Remove a `trace_id`.                                 |
| `/v1/clear`                | `GET`  | Clear everything.                                    |

### Proxy endpoints

| URL                | Method | Description                                                                            |
| ------------------ | ------ | -------------------------------------------------------------------------------------- |
| `/v1/proxy/report` | `POST` | Upsert operation to define a report for a `span_id` and `trace_id`.                    |
| `/v1/proxy/init`   | `POST` | Get a uid and configured fault (if any), based on the metadata available to the proxy. |

## Configuration

The environment can be configured using environment variables:

| Key                 | Default | Description                                                        |
| ------------------- | ------- | ------------------------------------------------------------------ |
| `CONTROLLER_PORT`   | `5000`  | The port on which the API is exposed.                              |
| `PROXY_TIMEOUT`     | `100`   | Max timeout before retrying requests to proxies.                   |
| `PROXY_RETRY_COUNT` | `3`     | Max number of retries to attempt when sending requests to proxies. |
| `LOG_LEVEL`         | `info`  | Log level: `error`, `warn`, `info` or `debug`.                     |
| `USE_OTEL`          | `false` | Whether to use OTel internally.                                    |

If `USE_OTEL` is set to `true`, you can configure [OpenTelemetry configurations](https://opentelemetry.io/docs/specs/otel/configuration/sdk-environment-variables/) in the environment too.
