# Reynard for TypeScript

Runs a test once per faultload of a Reynard exploration, through the [explorer's HTTP API](../../explorer/README.md#http-api). `explore` does not depend on a test framework, so it works the same in vitest and jest.

## Usage

Start the explorer next to the [instrumentation](../../instrumentation/):

```sh
java -jar explorer-<version>-cli.jar serve --controller http://localhost:5000 --port 7070
```

Then call `explore` in a test. It runs `test` once per faultload, with the headers to send with the request under test. A thrown error (e.g. a failed `expect`) marks the run as failed. When the exploration is done, `explore` resolves with the report, or rejects with an `InvariantViolatedError` that lists the violating faultloads.

```ts
import { explore } from '@reynard-testing/explorer';

test('checkout pays only for shipped orders', () =>
  explore({
    explorerUrl: 'http://localhost:7070',
    config: { name: 'checkout', maxTestCases: 100 },
    test: async ({ headers }) => {
      // Given - a user with a cart
      const userId = await newUserWithCart();

      // When - the user checks out
      await fetch(`http://localhost:5000/checkout?userId=${userId}`, { method: 'POST', headers });

      // Then - either the user paid and the order shipped, or neither happened
      expect(await paymentProcessed(userId)).toBe(await orderShipped(userId));
    },
  }));
```

`config` takes the same options as `@FiTest`, see [ExplorerConfig](../../explorer/src/main/java/dev/reynard/explorer/ExplorerConfig.java).

## Development

```sh
npm install
npm run typecheck
npm test
```

The integration test starts the controller and two instrumented [micro-benchmark services](../../util/micro-benchmarks/services/) with testcontainers, and runs the explorer jar on the host. It requires Docker, Java 17 and the explorer jar (`mvn -pl explorer -am package`). Override the images and jar with `CONTROLLER_IMAGE`, `PROXY_IMAGE` and `EXPLORER_JAR`, e.g. to test local builds:

```sh
CONTROLLER_IMAGE=fit-controller:latest PROXY_IMAGE=fit-proxy:latest npm test
```
