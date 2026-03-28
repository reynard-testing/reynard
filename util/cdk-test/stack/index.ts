import * as cdk from "aws-cdk-lib";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as ecs from "aws-cdk-lib/aws-ecs";
import * as ecs_patterns from "aws-cdk-lib/aws-ecs-patterns";
import * as lambda from "aws-cdk-lib/aws-lambda";
import { Construct } from "constructs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";

// Paths relative to this file's directory
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.join(__dirname, "../../../");
const INSTRUMENTATION_DIR = path.join(projectRoot, "instrumentation");

const logLevel = "DEBUG";

class ReynardTestStack extends cdk.Stack {
  private addProxyForLambda(
    targetLambda: lambda.Function,
    controllerHost: string,
  ) {
    const targetUrl = targetLambda.addFunctionUrl({
      // Note: this is insecure, don't keep it open!
      authType: lambda.FunctionUrlAuthType.NONE,
    });

    // Create a lambda proxy that forwards requests to the target lambda, and reports to the controller
    const lambdaProxy = new lambda.Function(
      this,
      `${targetLambda.node.id}ReynardProxy`,
      {
        runtime: lambda.Runtime.PROVIDED_AL2023,
        handler: "bootstrap",
        code: lambda.Code.fromAsset(
          path.join(
            INSTRUMENTATION_DIR,
            "lambda-proxy/lambda/reynard-lambda-proxy.zip",
          ),
        ),
        environment: {
          PROXY_TARGET: targetUrl.url,
          SERVICE_NAME: `${targetLambda.node.id}-proxy`,
          CONTROLLER_HOST: controllerHost,
          LOG_LEVEL: logLevel,
        },
      },
    );

    targetLambda.grantInvoke(lambdaProxy);

    const proxyUrl = lambdaProxy.addFunctionUrl({
      authType: lambda.FunctionUrlAuthType.NONE,
    });

    return { lambda: lambdaProxy, url: proxyUrl };
  }

  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // VPC for ECS
    const vpc = new ec2.Vpc(this, "Vpc", { maxAzs: 2 });
    const cluster = new ecs.Cluster(this, "Cluster", { vpc });

    const taskDef = new ecs.FargateTaskDefinition(this, "TaskDef", {
      memoryLimitMiB: 1024,
      cpu: 512,
    });

    taskDef.addContainer("Controller", {
      image: ecs.ContainerImage.fromAsset(INSTRUMENTATION_DIR, {
        file: "controller/Dockerfile",
      }),
      memoryLimitMiB: 1024,
      portMappings: [{ containerPort: 5000 }],
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: "reynard-controller" }),
      environment: {
        LOG_LEVEL: logLevel,
      },
    });

    // ALB in front of the controller so lambdaProxy can reach it via a stable URL
    const controllerService =
      new ecs_patterns.ApplicationLoadBalancedFargateService(
        this,
        "ControllerService",
        {
          cluster,
          taskDefinition: taskDef,
          desiredCount: 1,
          listenerPort: 5000,
          healthCheckGracePeriod: cdk.Duration.seconds(60),
        },
      );
    controllerService.targetGroup.configureHealthCheck({
      path: "/health",
      healthyHttpCodes: "200",
    });
    const controllerHost = `${controllerService.loadBalancer.loadBalancerDnsName}:5000`;

    const otelLayerArn = `arn:aws:lambda:${this.region}:184161586896:layer:opentelemetry-nodejs-0_20_0:1`;
    const otelLayer = lambda.LayerVersion.fromLayerVersionArn(
      this,
      "OtelLayer",
      otelLayerArn,
    );

    // Allow lambdaProxy to invoke the controller service
    controllerService.service.connections.allowFromAnyIpv4(ec2.Port.tcp(5000));

    // Lambda B: leaf micro-benchmark service
    const lambdaLeaf = new lambda.Function(this, "LambdaLeaf", {
      runtime: lambda.Runtime.NODEJS_20_X,
      handler: "index.handler",
      code: lambda.Code.fromInline(
        'exports.handler = async () => ({ statusCode: 200, body: "OK" });',
      ),
      environment: {
        OTEL_SERVICE_NAME: "Leaf",
      },
    });

    const leafProxy = this.addProxyForLambda(lambdaLeaf, controllerHost);

    const lambdaForwardToLeaf = new lambda.Function(
      this,
      "LambdaForwardToLeaf",
      {
        runtime: lambda.Runtime.NODEJS_20_X,
        handler: "index.handler",
        code: lambda.Code.fromAsset(
          path.join(__dirname, "../lambda/handler.zip"),
        ),
        layers: [otelLayer],
        environment: {
          AWS_LAMBDA_EXEC_WRAPPER: "/opt/otel-handler",
          OTEL_SERVICE_NAME: "ForwardToLeaf",
          OTEL_PROPAGATORS: "tracecontext,baggage",
          OTEL_TRACES_EXPORTER: "none",
          OTEL_METRICS_EXPORTER: "none",
          TARGET_URL: leafProxy.url.url,
        },
      },
    );

    leafProxy.lambda.grantInvoke(lambdaForwardToLeaf);

    const forwardProxy = this.addProxyForLambda(
      lambdaForwardToLeaf,
      controllerHost,
    );

    // Outputs
    new cdk.CfnOutput(this, "LambdaLeafArn", { value: lambdaLeaf.functionArn });
    new cdk.CfnOutput(this, "ControllerHost", { value: controllerHost });
    new cdk.CfnOutput(this, "EcsClusterName", { value: cluster.clusterName });
    new cdk.CfnOutput(this, "EcsServiceName", {
      value: controllerService.service.serviceName,
    });
    new cdk.CfnOutput(this, "ForwardProxyUrl", { value: forwardProxy.url.url });
  }
}

const app = new cdk.App();
new ReynardTestStack(app, "ReynardTestStack");
app.synth();
