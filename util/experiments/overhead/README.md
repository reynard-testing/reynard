# Overhead benchmark

This directory contains a setup to measure the overhead of Reynard.

It tests the interaction between a client, a proxy, the controller and a proxied services.
To minimize the influence of the proxied service, we provide minimal golang-based dummy service, that aditionally functions as a mock for the controller.

## Test scenarios:

- _Direct to service_, bypassing the proxy.
  Measures the throughput and latency contribution of the proxied service.

- _Missing headers_ Requests are sent to the proxy, but do not contain any instrumentation headers.
  These requests are directly proxied after inspecting the headers.
  Measures the throughput and latency contribution of proxying a request.

- _Unknown trace_ Requests contain instrumentation headers, but for an unknown trace.
  Compared to the previous scenario, the requests are directly proxied after a more thorough inspection.

- _Known trace, no fault_ Requests contain headers for a known trace, but the request does not match a fault injection instruction.
  This introduces additional overhead, as an identifier must be determined using the controller.

- _Known trace, no fault, mocked controller_ The controller is mocked.
  Indicates the overhead introduced by the controller.

- _Matching fault_ Requests contain headers for a known trace, for which there is a fault injection instruction.
  Measure the overhead of injecting faults.

- _Matching fault in large faultload_ The request identifier matches a fault instruction at the end of the lists of faults.
  Measures the overhead of matching a fault identifier in a large faultload.

## Requirements

- [wrk](https://github.com/wg/wrk)
- Docker + Compose

## Experiment

Run `service_overhead.sh`
