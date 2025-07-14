# Reynard Instrumentation

This directory contains the Go instrumentation code.
The instrumentation is made up of two components:
- A (reverse) [proxy](./proxy/) that is meant to be placed as a side-car to services of interest.
It is capable of intercepting HTTP and gRPC messages, and conditionally injecting faults by responding with erroneous messages.
- A [controller](./controller/) that exposes a central API for the test library to setup faults, as well as cooperate with the proxies to track the state of interactions in order to define consistent identifiers for events.

As there is overlap in the logic and code used by the instrumentation, there is a shared code folder.

## Local development

Use the `make build-all` or `make build-[proxy|controller]` commands in the _project root_ to build a tagged new version.
