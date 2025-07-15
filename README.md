# <img src="docs/images/reynard-logo.png" alt="Logo" width="64" align="center" /> Reynard

![GitHub License](https://img.shields.io/github/license/reynard-testing/reynard)
![GitHub Tag](https://img.shields.io/github/v/tag/reynard-testing/reynard)

Reynard is an automated fault injection testing tool for Microservice or Service-Oriented Architectures. It allows for the automatic exploration of the effect of combinations of partial failures on a system interaction.

Reynard consists of the following primary components:

- [Instrumentation](instrumentation/) to allow deterministic fault injection and inspect inter-service communication: a reverse proxy is placed as a side-car to services of interest, while a controller exposes an API for fault injection testing.
- A [testing library](lib/) to automatically explore relevant combinations of faults. This is implemented as a Java JUnit 5 decorator.

## Example

Considere a webshop designed as a Microservice Architecture. In a test environment, we have added the required instrumentation of Reynard. For the checkout endpoint, we want to ensure that failures do not cause a payment for an order that is never shipped. Using Reynard, we can create the following test suite:

```java
@FiTest
void testCheckout(TrackedFaultload faultload) throws IOException {
  // Given - a user with a cart
  int userId = newUser();
  addItemToCart(userId, productId, quantity);

  Request request = faultload.newRequestBuilder()
          .url("http://localhost:5000/checkout?userId=" + userId)
          .build();

  // When - we perform a checkout for the user
  try (Response response = client.newCall(request).execute()) {
      // Given - the system state after the interaction
      boolean paymentProcessed = PaymentService.getPaymentProcessedFor(userId);
      boolean orderShipped = ShippingService.getOrderShippedFor(userId);

      // Then - either the user payed, and the order is shipped, or neither ocurred
      // Otherwise a user would be paying for nothing, or a order is shipped without payment
      assertTrue((paymentProcessed && orderShipped) || (!paymentProcessed && !orderShipped));
  }
}
```

Reynard will automatically plan distinct combinations of faults to verify the system's behaviour.

## Capabilities

### Precise Fault Injection

Reynard's instrumentation can precisely identify events in the system and injection faults for those events. For example, we can inject faults based on temporal and causal relation, e.g., the event when service `X` sends a second request to a specific endpoint of service `Y` when called by service `Z`.

### Broad Applicablility

Reynard can function in any system that supports modern distributed tracing, can be configured with a reverse-proxy side-card pattern (similar to another common tool in MA: service meshes), and uses HTTP or gRPC for inter-service communication.

### Automated Exhaustive Testing

Reynard is capable of automatically discovering all fault injection points, and dynamically analyzes the system behaviour to cover all distinct combinations of faults that can input the outcome of a test scenario.

## Installation

### Adding Instrumentation

To correctly use Reynard, we require the addition of instrumentation to the the system's runtime.
In particular we require two types of instrumentation:

- The addition of [OpenTelemetry](https://opentelemetry.io/) for each service of interest, or any tracing solution that allows for both the propagation of the `traceparent` and `tracecontext` headers [as defined by the W3C](https://www.w3.org/TR/trace-context/).
- The addition of reverse proxies as a sidecar to each service of interest.
- The addition of a fault injection controller process.
- (Optionally) the addition of [Jaeger](https://www.jaegertracing.io/) to collect and visualize traces for debugging purposes.

**Distributed Tracing:**

We recommend [OpenTelemetry](https://opentelemetry.io/) as a distributed tracing solution as, in many cases, it can be [automatically added](https://opentelemetry.io/docs/zero-code/). Keep in mind that adding distributed tracing will introduce some overhead. Our tooling does not require that every span is exported, but it does require that context is correctly propagated.
Exporting spans to a tool like [Jaeger](https://www.jaegertracing.io/) will aid in debugging in case bugs are found using Reynard.

**Proxies:**

The reverse proxies are available as a [Docker image](https://hub.docker.com/r/dflipse/reynard-proxy). The exact deployment of the proxies depends on the system configuration. In most cases, you want to route traffic targeted to a service of interest through a proxy, without changing the services.
We provide a [converter utility](util/converter/) for Docker compose.
The supported communication protocols are HTTP1.1, HTTP2 and gRPC.

**Controller:**

The controller service is available as a [Docker image](https://hub.docker.com/r/dflipse/reynard-controller). It must be configured to be aware of all deployed proxies to function correctly.


**Local development:**

A local tagged version can be build using the `make build-all` command.
This results in two images: `fit-controller:latest` and `fit-proxy:latest`.

## Using the Test Library

The testing library is available as [a Maven package](https://central.sonatype.com/artifact/dev.reynard/junit) under the `dev.reynard.junit` namespace.
To create a Reynard test suite, simply add the `@FiTest` to a test function that has a single argument of type `TrackedFaultload`:

```java
@FiTest
void testSuite(TrackedFaultload faultload) { ... }
```

The faultload defines the current set of faults (starting with no faults), and the headers required for the instrumentation to understand how to inject these faults.

The test case must have the properties:

- It should be **repeatable**, as the test case is run repeatedly and it should be independent of previous executions.
- An interaction that can be subject to faults must include the headers defined in the tracked faultload. If you use `okhttp`, you can use the utility function `TrackedFaultload.newRequestBuilder` to automatically add the right headers.
- Only one system interaction should use the faultload headers, as not to confuse the testing library. The test suite can make multiple calls to the system to prepare the system state.

## Limits

Reynard can work in a variety of scenarios, but is not complete or sound in the general case. As Reynard performs dynamic grey-box testing, it builds an understanding of the system's interaction, observing the effect of events. As services can behave in any kind of way, there are limits to what Reynard can understand. Below are a number of known limitations:

- Reynard can handle **concurrency and asynchronicity**, but only if each request is uniquely identifiable. Otherwise, two events might swap identifiers and their effects will be wrongly attributed.
- Reynard assumes that the **effect of an event is deterministic**. For example, if a service performs two requests on an endpoint invocation, and Reynard observes that a failure of the first request omits the second request, then it assumes this always holds. Note that concurrency can result in nondeterministic behaviour of effects.
- Reynard assumes that responses with the **same status code will result in the same behaviour**. Systems that use the response body to indicate specific failures will result in an unsound exploration.
- Reynard does not correctly understand asynchronous communication patterns (such as message queues), as it does not capture the creation of an event, nor when an event is fully consumed. It will only be able to reason about events that occurred during the synchronous request to the system.

If a system does not conform to these limits, then Reynard can test combinations of faults that are infeasible to combine (hence redundant), or might omit cases. Still, it will be able to explore interesting combinations of faults.

Please see [the readme for the testing library](lib/) what can be configured to work around some of these limitations.
