import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { createServer } from 'node:net';
import { fileURLToPath } from 'node:url';
import { setTimeout } from 'node:timers/promises';
import { GenericContainer, Network, type StartedNetwork, type StartedTestContainer } from 'testcontainers';

const CONTROLLER_IMAGE = process.env.CONTROLLER_IMAGE || 'dflipse/reynard-controller:latest';
const PROXY_IMAGE = process.env.PROXY_IMAGE || 'dflipse/reynard-proxy:latest';
const EXPLORER_JAR =
  process.env.EXPLORER_JAR || fileURLToPath(new URL('../../../explorer/target/explorer-0.3.0-cli.jar', import.meta.url));
const SERVICES_DIR = fileURLToPath(new URL('../../../util/micro-benchmarks/services', import.meta.url));

export type InstrumentedSystem = {
  /** URL of the entry service, through its proxy */
  readonly entryUrl: string;
  readonly explorerUrl: string;
  readonly stop: () => Promise<void>;
};

/**
 * Starts the controller, `a` (pass-through to `b`, falls back to `fallbackValue`) and `b` (leaf, responds with
 * `response`), both behind a proxy, and an explorer on the host.
 */
export async function startSystem({ fallbackValue, response }: { fallbackValue: string; response: string }) {
  if (!existsSync(EXPLORER_JAR)) {
    throw new Error(`No explorer jar at ${EXPLORER_JAR}, build it with: mvn -pl explorer -am package`);
  }

  const network = await new Network().start();
  const controller = await new GenericContainer(CONTROLLER_IMAGE)
    .withNetwork(network)
    .withNetworkAliases('controller')
    .withEnvironment({ OTEL_SDK_DISABLED: 'true' })
    .withExposedPorts(5000)
    .start();

  const [b, a] = await Promise.all([
    instrument(network, 'b', microservice('leaf').then((image) => image.withEnvironment({ RESPONSE: response }))),
    instrument(
      network,
      'a',
      microservice('pass-through').then((image) =>
        image.withEnvironment({ TARGET_SERVICE_URL: 'http://b:8080/', FALLBACK_VALUE: fallbackValue }),
      ),
    ),
  ]);

  const controllerUrl = `http://${controller.getHost()}:${controller.getMappedPort(5000)}`;
  const port = await freePort();
  const explorer = spawn('java', ['-jar', EXPLORER_JAR, 'serve', '--controller', controllerUrl, '--port', `${port}`], {
    stdio: ['ignore', 'ignore', 'inherit'],
  });
  const explorerUrl = `http://localhost:${port}`;
  await waitUntilServing(explorerUrl);

  return {
    entryUrl: `http://${a.proxy.getHost()}:${a.proxy.getMappedPort(8080)}/`,
    explorerUrl,
    stop: async () => {
      explorer.kill();
      await Promise.all([a, b].flatMap(({ proxy, service }) => [proxy.stop(), service.stop()]));
      await controller.stop();
      await network.stop();
    },
  } satisfies InstrumentedSystem;
}

/** Builds (or reuses) the micro-benchmark image the JUnit integration tests use too. */
function microservice(serviceName: string) {
  return GenericContainer.fromDockerfile(SERVICES_DIR)
    .withBuildArgs({ SERVICE_NAME: serviceName })
    .withCache(true)
    .build(`local/fit-microbenchmark-${serviceName}`, { deleteOnExit: false })
    .then((image) => image.withExposedPorts(8080));
}

/** Starts the service as `<hostname>-instrumented`, and a proxy for it as `<hostname>`. */
async function instrument(
  network: StartedNetwork,
  hostname: string,
  service: Promise<GenericContainer>,
): Promise<{ proxy: StartedTestContainer; service: StartedTestContainer }> {
  const startedService = await (await service)
    .withNetwork(network)
    .withNetworkAliases(`${hostname}-instrumented`)
    .start();
  const proxy = await new GenericContainer(PROXY_IMAGE)
    .withNetwork(network)
    .withNetworkAliases(hostname)
    .withEnvironment({
      PROXY_HOST: '0.0.0.0:8080',
      PROXY_TARGET: `http://${hostname}-instrumented:8080`,
      CONTROLLER_HOST: 'controller:5000',
      SERVICE_NAME: hostname,
    })
    .withExposedPorts(8080)
    .start();
  return { proxy, service: startedService };
}

function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = createServer().listen(0, () => {
      const address = server.address();
      server.close(() => (typeof address === 'object' && address ? resolve(address.port) : reject(new Error('No port'))));
    });
  });
}

/** Polls the explorer until it answers, an unknown session gives a 404. */
async function waitUntilServing(explorerUrl: string, timeoutMs = 30_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const response = await fetch(`${explorerUrl}/v1/sessions/none/report`).catch(() => undefined);
    if (response?.status === 404) {
      return;
    }
    await setTimeout(250);
  }
  throw new Error(`Explorer did not start at ${explorerUrl} within ${timeoutMs}ms`);
}
