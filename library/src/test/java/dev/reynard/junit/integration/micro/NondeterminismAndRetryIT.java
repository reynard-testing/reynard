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
public class NondeterminismAndRetryIT {
    public static final InstrumentedApp app = new InstrumentedApp().withJaeger();

    // B is called with a retry (n=2)
    @Container
    private static final InstrumentedService serviceB = app.instrument("b-retry", 8080,
            MicroBenchmarkContainer.Leaf());

    @Container
    private static final InstrumentedService serviceC = app.instrument("c-leaf", 8080,
            MicroBenchmarkContainer.Leaf());

    // In parallel: call B with retry || call C
    private static final ActionComposition action = ActionComposition.Parallel(
            // Call B
            new ServerAction(MicroBenchmarkContainer.call(serviceB.getHostname()),
                    // With a Retry to B
                    new ServerAction(MicroBenchmarkContainer.call(serviceB.getHostname()))),
            // Call C
            new ServerAction(MicroBenchmarkContainer.call(serviceC.getHostname())));

    @Container
    private static final InstrumentedService serviceA = app.instrument("a-backend", 8080,
            MicroBenchmarkContainer.Complex(action))
            .withExposedPorts(8080);

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

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

    public void testA(TrackedFaultload faultload) throws IOException {
        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + serviceA.getMappedPort(8080) + "/")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String inspectUrl = app.controllerInspectUrl + "/v1/trace/" + faultload.getTraceId();

            TraceAnalysis result = getController().getTrace(faultload);
            // assert that we do not inject redundant faults
            Set<Fault> injectedFaults = result.getInjectedFaults();
            assertEquals(faultload.getFaultload().faultSet(), injectedFaults);

        }
    }

    @FiTest(withPredecessors = true)
    public void testCs(TrackedFaultload faultload) throws IOException {
        try {
            testA(faultload);
        } catch (AssertionError e) {
            // Due to the predecessors icm parallel, the number of completed events varies
            // Note: this test is flaky by design, as it tests nondeterminism
            // and retries, which can lead to different outcomes.
            throw e;
        }
    }

    @FiTest()
    public void testDefault(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true)
    public void testOpt(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

}
