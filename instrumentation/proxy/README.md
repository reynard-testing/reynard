# Instrumentation Proxy

## Configuration

The environment can be configured using environment variables:

| Key            | Default | Description                                                                                    |
| -------------- | ------- | ---------------------------------------------------------------------------------------------- |
| `PROXY_HOST`   | `""`    | The host of how the proxy runs (i.e., `"localhost:5000"`).                                     |
| `PROXY_TARGET` | `""`    | The target of proxied requests (i.e., `"service-a:5050"`)                                      |
| `SERVICE_NAME` | `""`    | The short unique name of the target service. As fallback, the target is used to define a name. |
| `LOG_LEVEL`    | `info`  | Log level: `error`, `warn`, `info` or `debug`.                                                 |
