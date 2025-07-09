# Overhead benchmark

This directory contains a setup to measure the overhead of Reynard.

It tests the interaction between a client, a proxy, the controller and a proxied services.
To minimize the influence of the proxied service, we provide minimal golang-based dummy service, that aditionally functions as a mock for the controller.

## Requirements

- [wrk](https://github.com/wg/wrk)
