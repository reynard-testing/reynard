package io.github.delanoflipse.fit.suite.integration.micro;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.github.delanoflipse.fit.suite.FiTest;
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
public class ResiliencePatterns {
    public static final InstrumentedApp app = new InstrumentedApp().withJaeger();

    // B is called with a retry (n=2)
    @Container
    private static final InstrumentedService serviceB = app.instrument("service-b", 8080,
            MicroBenchmarkContainer.Leaf());

    // C uses local fallbacks (both for C, and D)
    @Container
    private static final InstrumentedService serviceD = app.instrument("service-d", 8080,
            MicroBenchmarkContainer.Leaf());

    @Container
    private static final InstrumentedService serviceC = app.instrument("service-c", 8080,
            MicroBenchmarkContainer.PassThrough(MicroBenchmarkContainer.call(serviceD.getHostname()),
                    "Default value for D"));

    // E has error propagation from F, and has fallback to G
    @Container
    private static final InstrumentedService serviceF = app.instrument("service-f", 8080,
            MicroBenchmarkContainer.Leaf());
    @Container
    private static final InstrumentedService serviceE = app.instrument("service-e", 8080,
            MicroBenchmarkContainer.PassThrough(MicroBenchmarkContainer.call(serviceF.getHostname())));

    @Container
    private static final InstrumentedService serviceG = app.instrument("service-g", 8080,
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
    private static final InstrumentedService serviceA = app.instrument("service-a", 8080,
            MicroBenchmarkContainer.Complex(action))
            .withExposedPorts(8080);

    private OkHttpClient client = new OkHttpClient.Builder()
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
            // boolean injectedFaults = result.getInjectedFaults();
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
