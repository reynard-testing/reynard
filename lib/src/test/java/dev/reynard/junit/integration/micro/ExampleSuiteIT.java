package dev.reynard.junit.integration.micro;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.reynard.junit.FiTest;
import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.instrumentation.FaultController;
import dev.reynard.junit.instrumentation.testcontainers.InstrumentedApp;
import dev.reynard.junit.instrumentation.testcontainers.services.InstrumentedService;
import dev.reynard.junit.integration.micro.setup.ActionComposition;
import dev.reynard.junit.integration.micro.setup.MicroBenchmarkContainer;
import dev.reynard.junit.integration.micro.setup.ServerAction;
import dev.reynard.junit.strategy.TrackedFaultload;
import dev.reynard.junit.strategy.util.TraceAnalysis;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Testcontainers(parallel = true)
public class ExampleSuiteIT {
  public static final InstrumentedApp app = new InstrumentedApp().withJaeger();

  // Dependency graph (in order from top to bottom):
  //
  // A
  // ├─ B (retry once on failure)
  // ├─ C (calls D, but has fallback value when D fails)
  // │ └─ D
  // ├─ E (pass-through to F)
  // │ └─ F
  // └─ G (called if E/F fails)
  //

  // B is called with a retry (n=2)
  @Container
  private static final InstrumentedService serviceB = app.instrument("b-retry", 8080,
      MicroBenchmarkContainer.Leaf());

  // C uses local fallbacks (both for C, and D)
  @Container
  private static final InstrumentedService serviceD = app.instrument("d-leaf", 8080,
      MicroBenchmarkContainer.Leaf());

  @Container
  private static final InstrumentedService serviceC = app.instrument("c-fallback", 8080,
      MicroBenchmarkContainer.PassThrough(MicroBenchmarkContainer.call(serviceD.getHostname()),
          "Default value for D"));

  // E has error propagation from F, and has fallback to G
  @Container
  private static final InstrumentedService serviceF = app.instrument("f-leaf", 8080,
      MicroBenchmarkContainer.Leaf());
  @Container
  private static final InstrumentedService serviceE = app.instrument("e-propagate", 8080,
      MicroBenchmarkContainer.PassThrough(MicroBenchmarkContainer.call(serviceF.getHostname())));

  @Container
  private static final InstrumentedService serviceG = app.instrument("g-fallback-f", 8080,
      MicroBenchmarkContainer.Leaf());

  private static final ActionComposition action = ActionComposition.Sequential(
      // Call B
      new ServerAction(MicroBenchmarkContainer.call(serviceB.getHostname()),
          // With a Retry to B
          new ServerAction(MicroBenchmarkContainer.call(serviceB.getHostname()))),
      // Call C
      new ServerAction(MicroBenchmarkContainer.call(serviceC.getHostname()), "Default value for C"),
      // Call E
      new ServerAction(MicroBenchmarkContainer.call(serviceE.getHostname()),
          // Call H as fallback
          new ServerAction(MicroBenchmarkContainer.call(serviceG.getHostname()))));

  @Container
  private static final InstrumentedService serviceA = app.instrument("a-backend", 8080,
      MicroBenchmarkContainer.Complex(action))
      .withExposedPorts(8080);

  // Http client to call service A
  private static final OkHttpClient client = new OkHttpClient.Builder()
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .build();

  // This method needs to be exposed statically for the FiTest annotation to work
  public static FaultController getController() {
    return app;
  }

  @BeforeAll
  static void setupServices() {
    app.start();
  }

  @AfterAll
  static void teardownServices() {
    app.stop();
  }

  @FiTest(optimizeForRetries = true)
  public void testA(TrackedFaultload faultload) throws IOException {
    // Given - The presence of the faults introduced by the faultload
    Request request = faultload.newRequestBuilder()
        .url("http://localhost:" + serviceA.getMappedPort(8080) + "/")
        .build();

    // Given - A request to service A
    try (Response response = client.newCall(request).execute()) {
      // Note: if you debug this test, you can use the following URLs
      String controllerUrl = app.controllerInspectUrl + "/v1/trace/" + faultload.getTraceId();
      String jaegerUrl = app.jaegerInspectUrl + "/trace/" + faultload.getTraceId();

      // When - We can retrieve the trace analysis from the controller
      TraceAnalysis result = getController().getTrace(faultload);

      // Then - We expect that we have applied all faults
      // I.e., no redundant faults were injected
      Set<Fault> injectedFaults = result.getInjectedFaults();
      assertEquals(faultload.getFaultload().faultSet(), injectedFaults);
    }
  }

}
