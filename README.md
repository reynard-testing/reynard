# <img src="docs/images/reynard-logo.png" alt="Logo" width="64" align="center" /> Reynard

![GitHub License](https://img.shields.io/github/license/reynard-testing/reynard)
![GitHub Tag](https://img.shields.io/github/v/tag/reynard-testing/reynard)

Reynard is an automated fault injection testing tool for Microservice or Service-Oriented Architecture. It allows the automatic exploration of the effect of partial failures on a system interaction.

Reynard consists of the following primary components:

- [Instrumentation](instrumentation/) to allow deterministic fault injection and inspect inter-service communication: a reverse proxy is placed as a side-car to services of interest, while a controller exposes an API for fault injection testing.
- A [testing library](lib/) to automatically explore relevant combinations of faults. This is implemented as a Java JUnit 5 decorator.

## Example

Considere a webshop, designed as a Microservice Architecture. For a checkout endpoint, we want to ensure that failures do not cause a payment for an order that is never shipped. Using Reynard, we can create the following test suite:

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
      // Given the system state after the interaction
      boolean paymentProcessed = PaymentService.getPaymentProcessedFor(userId);
      boolean orderShipped = ShippingService.getOrderShippedFor(userId);

      // Then - Either the user payed, and the order is shipped, or neither ocurred
      // Otherwise a user would be paying for nothing, or a order is shipped without payment
      boolean valid = (paymentProcessed && orderShipped) || (!paymentProcessed && !orderShipped);
      assertTrue(valid);
  }
}
```

Reynard will automatically plan different combinations of faults to verify

## Capabilities

### Precise Fault Injection

Reynard's instrumentation can precisely capture the

## Installation

### Adding Instrumentation

To correctly use Reynard, we require the addition of instrumentation to the the system's runtime.
In particular we require two types of instrumentation:

- The addition of [OpenTelemetry](https://opentelemetry.io/) for each service of interest, or any tracing solution that allows for both the propagation of the `traceparent` and `tracecontext` headers [as defined by the W3C](https://www.w3.org/TR/trace-context/).
- The addition of reverse proxies as a sidecar to each service of interest.
- The addition of a fault injection controller process.
- (Optionally) the addition of Jaeger to collect and visualize traces for debugging purposes.

We recommend [OpenTelemetry](https://opentelemetry.io/) as a distributed tracing solution as, in many cases, it can be [automatically added](https://opentelemetry.io/docs/zero-code/). Keep in mind that adding distributed tracing will introduce some overhead. Our tooling does not require that every span is exported, but it does require that context is correctly propagated.

The reverse proxies are available as a [Docker image](https://hub.docker.com/r/dflipse/reynard-proxy). The exact deployment of the proxies depends on the system configuration. In most cases, you want to route traffic targeted to a service of interest through a proxy, without changing the services.
We provide a [converter utility](util/converter/) for Docker compose.
The supported communication protocols are HTTP1.1, HTTP2 and gRPC.

The controller service is available as a [Docker image](https://hub.docker.com/r/dflipse/reynard-controller). It must be configured to be aware of all deployed proxies to function correctly.

## Using the Test Library

The testing library is available as [a Maven package](https://central.sonatype.com/artifact/dev.reynard/junit) under the `dev.reynard.junit` namespace.
To create a Reynard test suite, simply add the `@FiTest` to a function that has a single argument of type `TrackedFaultload`:

```java
@FiTest
void testSuite(TrackedFaultload faultload) { ... }
```

The faultload defines the current set of faults (starting with no faults), and headers required for the instrumentation to understand which events to target.

The test case must have the properties:

- It should be **repeatable**, as the test case is run repeatedly and it should be independent of previous executions.
- An interaction that can be subject to faults must include the headers defined in the tracke faultload. If you use `okhttp`, you can use the utility function `TrackedFaultload.newRequestBuilder` to automatically add the right headers.
- Only one system interaction should use the faultload headers, as not to confuse the testing library. The test suite can make multiple calls to the system to prepare the system state.
