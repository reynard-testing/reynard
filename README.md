# <img src="docs/images/reynard-logo.png" alt="Logo" width="64" align="center" /> Reynard

![GitHub License](https://img.shields.io/github/license/reynard-testing/reynard)
![GitHub Tag](https://img.shields.io/github/v/tag/reynard-testing/reynard)
[![SWH](https://archive.softwareheritage.org/badge/origin/https://github.com/reynard-testing/reynard/)](https://archive.softwareheritage.org/browse/origin/?origin_url=https://github.com/reynard-testing/reynard)

Reynard is an automated fault injection testing tool for Microservice or Service-Oriented Architectures. It allows for the automatic exploration of the effect of combinations of partial failures on a system interaction.

Reynard consists of the following components:

- [Instrumentation](instrumentation/) to allow deterministic fault injection and inspect inter-service communication: a reverse proxy is placed as a side-car to services of interest, while a controller exposes an API for fault injection testing.
- A [testing library](library/) to automatically explore relevant combinations of faults. This is implemented as a Java JUnit 5 decorator.

## Experiments/Artifact Instruction

We have documented all steps needed to reproduce our results in [this readme](util/experiments/).

## Example Usage

Considere a webshop designed as a Microservice Architecture. In a test environment, we have added the required instrumentation of Reynard (see below). For the checkout endpoint, we want to ensure that failures do not cause a payment for an order that is never shipped. Using Reynard, we can create the following test suite:

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
      // Fetch the system's state after the interaction
      boolean paymentProcessed = PaymentService.getPaymentProcessedFor(userId);
      boolean orderShipped = ShippingService.getOrderShippedFor(userId);

      // Then - either the user payed, and the order is shipped, or neither ocurred
      // Otherwise a user would be paying for nothing, or a order is shipped without payment
      assertTrue((paymentProcessed && orderShipped) || (!paymentProcessed && !orderShipped));
  }
}
```

Reynard will automatically plan distinct combinations of faults to verify the system's behaviour.

### Trying it out

The simplest way to see Reynard in action is to run the [ExampleSuiteIT.java](/library/src/test/java/dev/reynard/junit/integration/micro/ExampleSuiteIT.java). Check out [the directory's readme](/library/src/test/java/dev/reynard/junit/integration/micro/README.md) with instructions on how to run it.

## Capabilities

### Precise Fault Injection

Reynard's instrumentation can precisely identify requests in the system and inject faults for those requests. We can inject faults based on temporal and causal relation, e.g., the second request sent by service `B` to a specific endpoint of service `C`, when a specific endpoint of `B` was called by service `A`.

### Broad Applicablility

Reynard can function in any system that supports modern distributed tracing, that can be configured with a reverse-proxy side-card pattern, and that uses HTTP or gRPC for inter-service communication.

### Automated Exhaustive Testing

Given an interaction with a single service's endpoint, Reynard can automatically detect all related fault injection points (requests), and dynamically analyzes the system behaviour to generate test scenarios covering all distinct combinations of faults that can influence the outcome of a service interaction.
It is able to do this for common communication patterns, but do note that there are [limitations](#limits) to what Reynard can detect and comprehend.

# Using Reynard

## 1. Installing Instrumentation

To use Reynard, we require the addition of the following instrumentation to the the system's runtime (in a local or test environment):

1. The addition of [OpenTelemetry](https://opentelemetry.io/) for each service of interest, or any tracing solution that allows for both the propagation of the `traceparent` and `tracecontext` headers [as defined by the W3C](https://www.w3.org/TR/trace-context/).
1. The addition of reverse proxies as a sidecar to each service of interest.
1. The addition of a fault injection controller process.
1. (Optionally) the addition of [Jaeger](https://www.jaegertracing.io/) to collect and visualize traces for debugging purposes.

#### 1.1. Distributed Tracing:

We recommend [OpenTelemetry](https://opentelemetry.io/) as a distributed tracing solution as, in many cases, it can be [automatically added](https://opentelemetry.io/docs/zero-code/) without source-code changes. Keep in mind that adding distributed tracing will introduce some computational overhead. Our tooling does not require that every span is exported, but it does require that context is correctly propagated.
Exporting spans to a tool like [Jaeger](https://www.jaegertracing.io/) will aid in debugging.

#### 1.2. Proxies:

The reverse proxies are available as a [Docker image](https://hub.docker.com/r/dflipse/reynard-proxy). The exact deployment of the proxies depends on the system configuration. In most cases, you want to route traffic targeted to a service of interest through a proxy, without changing the services.
We provide a [converter utility](util/converter/) for Docker compose.
The supported communication protocols are HTTP1.1, HTTP2 and gRPC. We currently do not support encrypted communication, but that should be feasible with some effort.

#### 1.3. Controller:

The controller service is available as a [Docker image](https://hub.docker.com/r/dflipse/reynard-controller). It must be [configured](/instrumentation/controller/README.md#configuration) to be aware of all deployed proxies to function correctly.

## 2. Using the Test Library

The exploration algorithm is implemented in Java.
You can apply Reynard even if your microservice system is not build Java.
The testing library is available as a [Maven package](https://central.sonatype.com/artifact/dev.reynard/junit) under the `dev.reynard.junit` namespace.

More details can be found in the [`/library` directory](/library/).

### 2.1 Enabling Fault Injection

A Reynard test suite requires that the test class implements a `public static FaultController getController()`, in order to control fault injection and analyse system behavior.
In most cases, you can just use a `new RemoteController(ControllerHost)`.

### 2.2. Creating a test scenario

Reynard test scenarios use the `@FiTest` decorator to automatically generate tests and take a `TrackedFaultload` as argument:

```java
@FiTest
void testSuite(TrackedFaultload faultload) { ... }
```

The "tracked faultload" defines the current set of faults (starting with no faults), and the headers required for the instrumentation to understand how to inject these faults.

A Reynard test function must have the properties:

- It should be **repeatable**, as the test function is run repeatedly and it should be independent of previous executions.
- If the dependencies of a request should be tested with faults, than the intial request must include the headers defined in the tracked faultload. If you use `okhttp`, you can use the utility function `TrackedFaultload.newRequestBuilder` to automatically add the right headers.
- Only one request should use the faultload headers, as not to confuse the testing library. The test function can make multiple calls to the system to prepare the system state, but only the request of interest should include the headers.

## Limits

Reynard can work in a variety of scenarios, but is not complete or sound in the general case. Reynard performs dynamic grey-box testing, building an understanding of the system's interaction by observing the effect of requests. As services can behave in any kind of way, there are limits to what Reynard can understand. Below are a number of known limitations:

- Reynard can handle **concurrency and asynchronicity** in some cases, but only if each request is uniquely identifiable. Otherwise, two requests might swap identifiers and their effects will be wrongly attributed.
- Reynard assumes that the **effect of a request is deterministic**. For example, if a service performs two requests on an endpoint invocation, and Reynard observes that a failure of the first request omits the second request, then it assumes this always holds. Note that concurrency can result in nondeterministic behaviour of effects.
- Reynard assumes that responses with the **same status code will result in the same behaviour**. Systems that use the response body to indicate specific failures will result in an unsound exploration.
- Reynard is made for request-response patterns. For asynchronous communication patterns (such as message queues), it can not capture the creation of an event as it does requests, resulting in a loss of causality and making it harder to correctly infer effects. Furthermore, Reynard by default only captures events that occurred during the synchronous request-response to the system. This results in possible non-determinism. This can be resolved if the test is able to wait untill all events are processed before collecting the events from the controller.

### Expected effects of limitations

- If request identifiers the system is non-deterministic, then Reynard will try to search an ever growing number of fault injection points, and will never inject the right faults. To resolve this, Reynard [can be configured](/library/README.md#configuration) to be less precise in its identifiers.
- If the effects of requests are non-deterministic. Reynard will wrongly infer the effects of an event. As a result, it will visit either combinations of faults that cannot be combined (false-positives), or omit combinations of faults that it inferred to be redundant (false-negatives). Hence, Reynard can be slower, and/or inexhaustive.
- If te responses with the same status code have different meanings, this will cause non-deterministic effects, which results in the behavior as described above.

Please see [the readme for the testing library](/library/) what can be configured to work around some of these limitations.
