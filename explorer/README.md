# Reynard Explorer

The explorer decides which combinations of faults to inject, and learns from the result of each run. It contains the fault model, the search strategy and the client for the [controller](../instrumentation/controller/).

It can be used in three ways:

- **In-process from JUnit:** the [`@FiTest`](../library/) decorator runs it inside the test.
- **CLI:** runs a command once per faultload. Works with tests in any language, without code changes beyond sending two headers.
- **HTTP API:** for test frameworks in other languages that drive the exploration from their own process.

## Building

```sh
mvn -pl explorer -am package
```

This produces `explorer/target/explorer-<version>-cli.jar`, a standalone jar with all dependencies.

## What a test must do

In every mode, a test run must:

1. Send the `traceparent` and `tracestate` headers of the run with the request under test (and only that request).
2. Report whether the invariant held.

## CLI

```sh
java -jar explorer-<version>-cli.jar explore --controller http://localhost:5000 \
  --config '{"name": "checkout", "maxTestCases": 100}' \
  -- pytest tests/test_checkout.py
```

The command runs once per faultload, with the headers in the `REYNARD_TRACEPARENT` and `REYNARD_TRACESTATE` environment variables. Exit code `0` means the invariant held.
The CLI exits with `1` if any run violated the invariant.

`--config` takes the same options as `@FiTest` as JSON, for example `maxTestCases`, `maxTimeS`, `failStop`, `optimizeForRetries` and `maskPayload`, plus `name` and `outputDir`. See [ExplorerConfig](./src/main/java/dev/reynard/explorer/ExplorerConfig.java).

## HTTP API

```sh
java -jar explorer-<version>-cli.jar serve --controller http://localhost:5000 --port 7070
```

| Call | Purpose |
| --- | --- |
| `POST /v1/sessions` `{config}` | Starts exploring one scenario. Returns `{session_id}`. |
| `POST /v1/sessions/{id}/next` | Returns `{run_id, headers: {traceparent, tracestate}}` for the next run, or `204` when the exploration is done. |
| `POST /v1/sessions/{id}/runs/{run_id}` `{passed, message}` | Records the verdict of a run. The explorer then fetches its trace and updates the search. |
| `GET /v1/sessions/{id}/report` | Returns `{name, done, runs, lost, failures}`. |

A client loops over `next` until it returns `204`, and posts a verdict for every run before asking for the next one.
