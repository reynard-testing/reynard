# Micro benchmarks

This directory contains benchmark microservices that can be composed to create micro benchmarks.
They are made in Typescript to be dynamic yet simple, and because OpenTelemetry supports zero-code instrumentation for Node.

All services have a single endpoint `GET /` running on port defined by the environment variable `PORT`.

There are four types of services:

- **Leaf** - Only outputs a pre-defined response. The response can be configured.
- **Pass-through** - Has a single dependency on another service. Can be configured to use an internall fallback value in case of errors.
- **Parallell** - Has a configurable number of dependencies that are requested in parallel.
- **Complex** - Takes a configurable JSON (mounted as `/action.json`) as input to define the behaviour of the endpoint.

### Complex configuration

The behaviour of the endpoint can be defined as a `ServerAction`, defined as:

```ts
type ServerAction =
  // A remote call
  | {
      endpoint: string;
      payload?: any;
      method: "GET" | "POST" | "PUT" | "DELETE";
      onFailure?: ServerAction;
    }
  // A composite action
  | ActionComposition
  // A default response
  | string;

// Composition of actions
type ActionComposition = {
  order: ActionOrder;
  actions: ServerAction[];
};

// Do actions in the composition run in parallel or sequential?
type ActionOrder = "parallel" | "sequential";
```

_Note: in theory every other type of service could be composed as a "complex" service_
