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
const PROXY_LAYER_DIR = path.join(INSTRUMENTATION_DIR, "proxy/lambda-layer");
const APP_DIR = path.join(__dirname, "../app");

const APP_PORT = 8080;
const PROXY_PORT = 8081;
const logLevel = "DEBUG";

class ReynardTestStack extends cdk.Stack {
  private webAdapterLayer: lambda.ILayerVersion;
  private proxyLayer: lambda.ILayerVersion;

  // Each function runs Lambda Web Adapter -> Reynard proxy -> app, all inside the same environment
  private addInstrumentedLambda(
    id: string,
    controllerHost: string,
    environment: Record<string, string> = {},
  ) {
    const fn = new lambda.Function(this, id, {
      runtime: lambda.Runtime.NODEJS_22_X,
      handler: "run.sh",
      code: lambda.Code.fromAsset(APP_DIR),
      layers: [this.webAdapterLayer, this.proxyLayer],
      environment: {
        AWS_LAMBDA_EXEC_WRAPPER: "/opt/bootstrap",
        AWS_LWA_PORT: `${PROXY_PORT}`,
        AWS_LWA_READINESS_CHECK_PATH: "/health",
        PORT: `${APP_PORT}`,
        PROXY_HOST: `127.0.0.1:${PROXY_PORT}`,
        PROXY_TARGET: `http://127.0.0.1:${APP_PORT}`,
        SERVICE_NAME: id,
        CONTROLLER_HOST: controllerHost,
        LOG_LEVEL: logLevel,
        ...environment,
      },
    });

    const url = fn.addFunctionUrl({
      // Note: this is insecure, don't keep it open!
      authType: lambda.FunctionUrlAuthType.NONE,
    });

    return { fn, url };
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

    // Public ALB in front of the controller, so the proxies and the test can reach it
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

    // shortcut: pinned to a fixed version and x86_64, bump when needed
    this.webAdapterLayer = lambda.LayerVersion.fromLayerVersionArn(
      this,
      "WebAdapterLayer",
      `arn:aws:lambda:${this.region}:753240598075:layer:LambdaAdapterLayerX86:25`,
    );
    this.proxyLayer = new lambda.LayerVersion(this, "ReynardProxyLayer", {
      code: lambda.Code.fromAsset(PROXY_LAYER_DIR),
      compatibleArchitectures: [lambda.Architecture.X86_64],
    });

    const leaf = this.addInstrumentedLambda("Leaf", controllerHost);
    const forward = this.addInstrumentedLambda("ForwardToLeaf", controllerHost, {
      TARGET_URL: leaf.url.url,
    });

    // Outputs
    new cdk.CfnOutput(this, "ControllerUrl", { value: `http://${controllerHost}` });
    new cdk.CfnOutput(this, "ForwardUrl", { value: forward.url.url });
  }
}

const app = new cdk.App();
new ReynardTestStack(app, "ReynardTestStack");
app.synth();
