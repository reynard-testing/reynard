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
## AWS Lambda

The proxy also runs as a [Lambda extension](https://docs.aws.amazon.com/lambda/latest/dg/lambda-extensions.html): when `AWS_LAMBDA_RUNTIME_API` is set, it registers itself with the Extensions API. Put it behind [AWS Lambda Web Adapter](https://github.com/awslabs/aws-lambda-web-adapter), so requests flow adapter → proxy → app on localhost:

| Key | Example |
| --- | ------- |
| `AWS_LWA_PORT` | `8081` (the proxy) |
| `PROXY_HOST` | `127.0.0.1:8081` |
| `PROXY_TARGET` | `http://127.0.0.1:8080` (the app) |

Build the layer with `make build-lambda-layer`, which exports it to `proxy/lambda-layer/`. See [util/cdk-test](../../util/cdk-test/) for a full example.
