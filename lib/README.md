# Reynard Testing Library

This directory contains the Java testing library code.
The directories within the project are structured as follows:

- **faultload/** contains (data) classes related to the definition of simulated faults.
- **instrumentation/** defines the interaction with the test library and the instrumentation. Additionally, it includes utility methods to use Reynard with [Testcontainers](https://testcontainers.com/).
- **strategy/** contains the components related to the automated test strategy.

In the root directory, you will find `FiTest` and `FiTestExtension` which define the JUnit 5 decorator and corresponding JUnit extension.

## Usage

The `@FiTest` decorator can be used to create fault injection tests.

### Requirements

- For the decorator to function properly, the test environment must be configured with Reynard instrumentation.
- A method with this decorator must take a single argument of type `TrackedFaultload`
- The class where the decorator belongs to must expose a static method of type `FaultController getController()` that returns a way to inject faults.

### Configuration

The decorator takes a wide variety of arguments:

| Parameter            | Default | Explanation                                                                                           |
| -------------------- | ------- | ----------------------------------------------------------------------------------------------------- |
| optimizeForRetries   | false   | Assume retries are implemented correctly to reduce the search space.                                  |
| maskPayload          | false   | Ignore the payload field in the identifier.                                                           |
| withPredecessors     | false   | Include the predecessors field in the identifier.                                                     |
| hashBody             | false   | Use a hash of the response body for identification.                                                   |
| failStop             | true    | Stop testing when a failing test is found.                                                            |
| maxTestCases         | 0       | Maximum number of test executions (0 means no limit).                                                 |
| maxTimeS             | 0       | Maximum time for tests in seconds (0 means no limit).                                                 |
| maxFaultloadSize     | 0       | Maximum size of faultloads (0 means no limit).                                                        |
| initialGetTraceDelay | 0       | Delay (in miliseconds) before retrieving reports from proxies, useful for asynchronous communication. |
| additionalComponents | []      | Array of custom components (e.g., analyzers, pruners) to add to the search strategy.                  |

There are more parameters, but these are for debugging and experimental purposes.

## Manual experimentation

In some cases, it might be usefull to perform manual experimentation. For example when dealing with a counter-example, this could be stored as a seperate test case.
The `@FiTest` decorate handles registering and unregistering a faultload for a test case. Without it, this must be done manually.

For example:

```java

@Test
public void testCheckoutCounterexample() throws IOException, URISyntaxException {
    FailureMode mode = ErrorFault.fromError(HttpError.SERVICE_UNAVAILABLE);
    FaultUid uid = new FaultUid(List.of(
        FaultInjectionPoint.Any(),
        FaultInjectionPoint.Any().withDestination("payment").withCount(0)));
    Faultload f = new Faultload(Set.of(new Fault(uid, mode)));
    TrackedFaultload tracked = new TrackedFaultload(f);

    controller.withFaultload(tracked, () -> {
        var res = testCheckout(tracked);
        assertFalse(res.code() == 404);
        return null;
    });
}
```

## Using with Testcontainers

## Custom components

<!-- Manual experimentation -->
<!-- Writing custom components -->
