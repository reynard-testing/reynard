# Reynard CDK Test Stack

An AWS CDK stack that deploys a minimal Reynard setup to AWS, used by the
[`AwsCdkSuiteIT`](../../integrations/junit/src/test/java/dev/reynard/junit/integration/AwsCdkSuiteIT.java)
integration test.

## Architecture

The stack deploys:

- **ECS Fargate**: runs the Reynard controller behind a public Application Load Balancer (port 5000).
- **Leaf**: a Lambda that returns `200 OK`.
- **ForwardToLeaf**: a Lambda that calls `Leaf` `TARGET_REPEATS` times (default 3), forwarding the `traceparent` and `tracestate` headers.

Both Lambdas run the same [app](./app/index.mjs) with two layers:
[AWS Lambda Web Adapter](https://github.com/awslabs/aws-lambda-web-adapter) and the Reynard proxy as a Lambda extension.
Requests flow Web Adapter (`:8081`) → proxy → app (`:8080`), all inside the function's environment.

> The function URLs and the controller are open to the internet. Destroy the stack after use.

## Prerequisites

- [AWS CDK](https://docs.aws.amazon.com/cdk/latest/guide/getting_started.html) and credentials for the target account
- Node.js dependencies: `npm install`
- The proxy layer: `make build-lambda-layer` from the repository root (exports to `instrumentation/proxy/lambda-layer/`)

## Usage

```bash
npm run bootstrap # once per account/region
npm run deploy
npm run destroy
```

The stack outputs map to the environment variables of `AwsCdkSuiteIT`:

| Output          | Variable         |
| --------------- | ---------------- |
| `ControllerUrl` | `CONTROLLER_URL` |
| `ForwardUrl`    | `BASE_URL`       |

```bash
cd integrations/junit
CONTROLLER_URL=... BASE_URL=... mvn test -Dtest=AwsCdkSuiteIT
```
