# Micro Benchmarks

This directory contains a number of micro-benchmark test suites. Using the microservices defined [here](util/micro-benchmarks), these suites can compose a benchmark system that has specific communication patterns. The [example suite](./ExampleSuiteIT.java) uses all possible types, so its a good starting point.
By using [testcontainers](https://testcontainers.com/), the setup is performed automatically.

## Requirements

- Java JDK 17
- Maven
- Docker

## How to run

Preferably, use your IDE to run/debug a specific suite, as this allows you to set breakpoints and investigate what each individual test does. However, if you do want to run it in the terminal, use `mvn test -Dtest=<class-name>#<test-method>`, e.g. `mvn test -Dtest=ExampleSuiteIT#testA`.

_Note: The first time you run a suite, it will pull all instrumentation image, and build all benchmark services. This will take in the order of minutes. The next time you run it, it will re-use these images, making it significantly faster._
