package io.github.delanoflipse.fit.suite.integration.micro;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.github.delanoflipse.fit.suite.FiTest;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.instrument.FaultController;
import io.github.delanoflipse.fit.suite.instrument.InstrumentedApp;
import io.github.delanoflipse.fit.suite.instrument.services.InstrumentedService;
import io.github.delanoflipse.fit.suite.integration.micro.setup.ActionComposition;
import io.github.delanoflipse.fit.suite.integration.micro.setup.MicroBenchmarkContainer;
import io.github.delanoflipse.fit.suite.integration.micro.setup.ServerAction;
import io.github.delanoflipse.fit.suite.strategy.TrackedFaultload;
import io.github.delanoflipse.fit.suite.strategy.util.TraceAnalysis;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@SuppressWarnings("resource")
@Testcontainers(parallel = true)
public class ResiliencePatternsIT {
    public static final InstrumentedApp app = new InstrumentedApp().withJaeger();

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

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public static FaultController getController() {
        return app;
    }

    @BeforeAll
    public static void setupServices() {
        app.start();
    }

    @AfterAll
    static public void teardownServices() {
        app.stop();
    }

    @FiTest()
    public void testA(TrackedFaultload faultload) throws IOException {
        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + serviceA.getMappedPort(8080) + "/")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String inspectUrl = app.controllerInspectUrl + "/v1/trace/" + faultload.getTraceId();

            TraceAnalysis result = getController().getTrace(faultload);
            // assert that we do not inject redundant faults
            Set<Fault> injectedFaults = result.getInjectedFaults();
            assertEquals(faultload.getFaultload().faultSet(), injectedFaults);
        }
    }

    @FiTest(withCallStack = true)
    public void testCs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true)
    public void testOpt(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true, withCallStack = true)
    public void testCsOpt(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

}
