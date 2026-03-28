# Reynard CDK Test Stack

An AWS CDK stack that deploys a minimal Reynard test infrastructure to AWS, used by the
[`AwsCdkSuiteIT`](../../library/src/test/java/dev/reynard/junit/integration/AwsCdkSuiteIT.java)
integration test.

## Architecture

The stack deploys:

- **ECS Fargate** — runs the Reynard controller behind an Application Load Balancer (port 5000).
- **LambdaLeaf** — a minimal Node.js leaf function (returns `200 OK`).
- **LambdaForwardToLeaf** — a Node.js forwarder function that calls `LambdaLeaf` N times (configured by `TARGET_REPEATS`). Uses the AWS OTel Lambda layer for trace propagation.
- **Reynard lambda proxies** — each Lambda has a sidecar proxy (`instrumentation/lambda-proxy`) deployed as a `PROVIDED_AL2023` runtime in front of it. The proxy intercepts calls, consults the controller, and injects faults.

## Prerequisites

- [AWS CDK](https://docs.aws.amazon.com/cdk/latest/guide/getting_started.html) installed
- Node.js dependencies: `npm install`
- Lambda proxy built: `../instrumentation/lambda-proxy/build.sh` (produces `lambda-proxy/lambda/reynard-lambda-proxy.zip`)

## Usage

```bash
# Package the forwarder lambda
npm run zip-lambda

# Deploy to AWS (bootstraps CDK if needed)
npm run bootstrap
npm run deploy

# Tear down
npm run destroy
```

Stack outputs include `ControllerHost` and `ForwardProxyUrl`, which map to the `CONTROLLER_HOST`
and `BASE_URL` environment variables expected by `AwsCdkSuiteIT`.