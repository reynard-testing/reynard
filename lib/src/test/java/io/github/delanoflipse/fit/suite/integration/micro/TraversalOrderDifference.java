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
import io.github.delanoflipse.fit.suite.strategy.util.traversal.TraversalOrder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@SuppressWarnings("resource")
@Testcontainers(parallel = true)
public class TraversalOrderDifference {
    public static final InstrumentedApp app = new InstrumentedApp().withJaeger();

    // B calls C (with retry)
    @Container
    private static final InstrumentedService serviceC = app.instrument("c-leaf", 8080,
            MicroBenchmarkContainer.Leaf());

    @Container
    private static final InstrumentedService serviceB = app.instrument("b-pass", 8080,
            MicroBenchmarkContainer.PassThrough(MicroBenchmarkContainer.call(serviceC.getHostname())));

    // D's fallback is E
    @Container
    private static final InstrumentedService serviceD = app.instrument("d-leaf", 8080,
            MicroBenchmarkContainer.Leaf());

    @Container
    private static final InstrumentedService serviceE = app.instrument("e-leaf", 8080,
            MicroBenchmarkContainer.Leaf());

    // F's fallback is E
    @Container
    private static final InstrumentedService serviceF = app.instrument("f-leaf", 8080,
            MicroBenchmarkContainer.Leaf());

    private static final ActionComposition action = ActionComposition.Sequential(
            // Call B
            new ServerAction(MicroBenchmarkContainer.call(serviceB.getHostname()),
                    // With a Retry to B
                    new ServerAction(MicroBenchmarkContainer.call(serviceB.getHostname()))),
            // Call D
            new ServerAction(MicroBenchmarkContainer.call(serviceD.getHostname()),
                    new ServerAction(MicroBenchmarkContainer.call(serviceE.getHostname()))),
            // Call F
            new ServerAction(MicroBenchmarkContainer.call(serviceF.getHostname()),
                    // Call E as fallback
                    new ServerAction(MicroBenchmarkContainer.call(serviceE.getHostname()))));

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
            // assertEquals(faultload.getFaultload().faultSet(), injectedFaults);
        }
    }

    // @FiTest(optimizeForRetries = true)
    // public void testOpt(TrackedFaultload faultload) throws IOException {
    // testA(faultload);
    // }

    // @FiTest(optimizeForRetries = true, withCallStack = true)
    // public void testCsOpt(TrackedFaultload faultload) throws IOException {
    // testA(faultload);
    // }

    @FiTest(optimizeForRetries = true, withCallStack = true, pointOrder = TraversalOrder.BREADTH_FIRST, depthFirstSearchOrder = true)
    public void testCsOptBfs_Dfs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = false, withCallStack = true, pointOrder = TraversalOrder.BREADTH_FIRST, depthFirstSearchOrder = true)
    public void testCsBfs_Dfs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true, withCallStack = true, pointOrder = TraversalOrder.BREADTH_FIRST, depthFirstSearchOrder = false)
    public void testCsOptBfs_Bfs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true, withCallStack = true, pointOrder = TraversalOrder.BREADTH_FIRST_REVERSE, depthFirstSearchOrder = true)
    public void testCsOptBfsInv_Dfs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true, withCallStack = true, pointOrder = TraversalOrder.BREADTH_FIRST_REVERSE, depthFirstSearchOrder = false)
    public void testCsOptBfsInv_Bfs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true, withCallStack = true, pointOrder = TraversalOrder.DEPTH_FIRST_POST_ORDER, depthFirstSearchOrder = false)
    public void testCsOptDfsPost_Bfs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true, withCallStack = true, pointOrder = TraversalOrder.DEPTH_FIRST_POST_ORDER, depthFirstSearchOrder = true)
    public void testCsOptDfsPost_Dfs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true, withCallStack = true, pointOrder = TraversalOrder.DEPTH_FIRST_PRE_ORDER, depthFirstSearchOrder = false)
    public void testCsOptDfsPre_Bfs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true, withCallStack = true, pointOrder = TraversalOrder.DEPTH_FIRST_PRE_ORDER, depthFirstSearchOrder = true)
    public void testCsOptDfsPre_Dfs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true, withCallStack = true, pointOrder = TraversalOrder.DEPTH_FIRST_REVERSE_PRE_ORDER, depthFirstSearchOrder = false)
    public void testCsOptDfsRevPre_Bfs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true, withCallStack = true, pointOrder = TraversalOrder.DEPTH_FIRST_REVERSE_PRE_ORDER, depthFirstSearchOrder = true)
    public void testCsOptDfsRevPre_Dfs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true, withCallStack = true, pointOrder = TraversalOrder.DEPTH_FIRST_REVERSE_POST_ORDER, depthFirstSearchOrder = false)
    public void testCsOptDfsRevPost_Bfs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }

    @FiTest(optimizeForRetries = true, withCallStack = true, pointOrder = TraversalOrder.DEPTH_FIRST_REVERSE_POST_ORDER, depthFirstSearchOrder = true)
    public void testCsOptDfsRevPost_Dfs(TrackedFaultload faultload) throws IOException {
        testA(faultload);
    }
}
