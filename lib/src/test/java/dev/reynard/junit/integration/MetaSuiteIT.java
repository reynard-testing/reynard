package dev.reynard.junit.integration;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.reynard.junit.FiTest;
import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultInjectionPoint;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.faultload.Faultload;
import dev.reynard.junit.faultload.modes.ErrorFault;
import dev.reynard.junit.faultload.modes.FailureMode;
import dev.reynard.junit.faultload.modes.HttpError;
import dev.reynard.junit.instrumentation.FaultController;
import dev.reynard.junit.instrumentation.testcontainers.InstrumentedApp;
import dev.reynard.junit.instrumentation.testcontainers.services.ControllerService;
import dev.reynard.junit.instrumentation.testcontainers.services.InstrumentedService;
import dev.reynard.junit.strategy.TrackedFaultload;
import dev.reynard.junit.strategy.components.PruneContext;
import dev.reynard.junit.strategy.components.PruneDecision;
import dev.reynard.junit.strategy.components.Pruner;
import dev.reynard.junit.strategy.util.Env;
import dev.reynard.junit.strategy.util.TraceAnalysis;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@SuppressWarnings("resource")
@Testcontainers(parallel = true)
public class MetaSuiteIT {
    public static final InstrumentedApp app = new InstrumentedApp().withJaeger();
    private static final int PROXY_RETRY_COUNT;
    private static final String LOG_LEVEL;
    private static final String PROXY_IMAGE = InstrumentedService.IMAGE;
    private static final String CONTROLLER_IMAGE = ControllerService.IMAGE;
    private static final String COLLECTOR_ENDPOINT = "http://" + app.jaegerHost + ":4317";
    public static final MediaType JSON = MediaType.get("application/json");

    private OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .build();
    private final static TrackedFaultload metaFaultload = new TrackedFaultload();
    private final static String jsonBody = metaFaultload.serializeJson();
    private final static RequestBody body = RequestBody.create(jsonBody, JSON);

    static {
        PROXY_RETRY_COUNT = Integer.parseInt(Env.getEnv("PROXY_RETRY_COUNT", "3"));
        LOG_LEVEL = Env.getEnv("LOG_LEVEL", "debug");
    }

    @Container
    private static final InstrumentedService proxy1 = app.instrument("proxy1", 8050,
            new GenericContainer<>(PROXY_IMAGE)
                    .withEnv("PROXY_HOST", "0.0.0.0:8080")
                    .withEnv("PROXY_TARGET", "http://0.0.0.0:8090")
                    .withEnv("CONTROLLER_HOST", "controller:5000")
                    .withEnv("CONTROL_PORT", "8050")
                    .withEnv("USE_OTEL", "true")
                    .withEnv("OTEL_SERVICE_NAME", "proxy1-real")
                    .withEnv("LOG_LEVEL", LOG_LEVEL)
                    .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", COLLECTOR_ENDPOINT));

    @Container
    private static final InstrumentedService proxy2 = app.instrument("proxy2", 8050,
            new GenericContainer<>(PROXY_IMAGE)
                    .withEnv("PROXY_HOST", "0.0.0.0:8080")
                    .withEnv("PROXY_TARGET", "http://0.0.0.0:8090")
                    .withEnv("CONTROLLER_HOST", "controller:5000")
                    .withEnv("CONTROL_PORT", "8050")
                    .withEnv("USE_OTEL", "true")
                    .withEnv("OTEL_SERVICE_NAME", "proxy2-real")
                    .withEnv("LOG_LEVEL", LOG_LEVEL)
                    .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", COLLECTOR_ENDPOINT));

    @Container
    private static final InstrumentedService proxy3 = app.instrument("proxy3", 8050,
            new GenericContainer<>(PROXY_IMAGE)
                    .withEnv("PROXY_HOST", "0.0.0.0:8080")
                    .withEnv("PROXY_TARGET", "http://0.0.0.0:8090")
                    .withEnv("CONTROLLER_HOST", "controller:5000")
                    .withEnv("CONTROL_PORT", "8050")
                    .withEnv("USE_OTEL", "true")
                    .withEnv("OTEL_SERVICE_NAME", "proxy3-real")
                    .withEnv("LOG_LEVEL", LOG_LEVEL)
                    .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", COLLECTOR_ENDPOINT));

    @Container
    private static final InstrumentedService controller = app.instrument("controller", 5000,
            new GenericContainer<>(CONTROLLER_IMAGE)
                    .withEnv("PROXY_LIST", "proxy1:8050,proxy2:8050,proxy3:8050")
                    .withEnv("PROXY_RETRY_COUNT", "" + PROXY_RETRY_COUNT)
                    .withEnv("PROXY_TIMEOUT", "0")
                    .withEnv("USE_OTEL", "true")
                    .withEnv("OTEL_SERVICE_NAME", "controller-real")
                    .withEnv("LOG_LEVEL", LOG_LEVEL)
                    .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", COLLECTOR_ENDPOINT))
            .withExposedPorts(5000);

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

    @Test
    public void testShouldRetry() throws IOException {
        int port = controller.getMappedPort(5000);
        String queryUrl = "http://localhost:" + port + "/v1/faultload/register";

        // First call from the controller to proxy1
        FaultUid uid = new FaultUid(List.of(controller.getPoint(), proxy1.getPoint().withCount(0)));
        FailureMode mode = ErrorFault.fromError(HttpError.SERVICE_UNAVAILABLE);
        Faultload faultload = new Faultload(Set.of(new Fault(uid, mode)));
        TrackedFaultload tracked = new TrackedFaultload(faultload);
        String inspectUrl = app.controllerInspectUrl + "/v1/trace/" + tracked.getTraceId();

        Request request = new Request.Builder()
                .url(queryUrl)
                .post(body)
                .addHeader("traceparent", tracked.getTraceParent().toString())
                .addHeader("tracestate", tracked.getTraceState().toString())
                .build();

        app.registerFaultload(tracked);

        try (Response response = client.newCall(request).execute()) {
            TraceAnalysis result = getController().getTrace(tracked);
            boolean hasRetry = result.getFaultUids()
                    .stream()
                    .anyMatch(f -> f.matches(uid.asAnyCount()) && f.count() == 1);
            assertEquals(200, response.code());
            assertTrue(hasRetry);
        }
    }

    static public class Proxy3Ignorer implements Pruner {
        @Override
        public PruneDecision prune(Faultload faultload, PruneContext context) {
            for (FaultUid uid : faultload.getFaultUids()) {
                if (!uid.getPoint().destination().equals("proxy1")) {
                    return PruneDecision.PRUNE_SUPERSETS;
                }
            }
            return PruneDecision.KEEP;
        }

    }

    @FiTest(maxTestCases = 999, optimizeForRetries = true, withPredecessors = false, additionalComponents = {
            Proxy3Ignorer.class })
    public void testRegisterWithCustomPruner(TrackedFaultload faultload) throws IOException {
        testRegister(faultload);
    }

    @FiTest(maxTestCases = 99999, optimizeForRetries = false, withPredecessors = false)
    public void testRegisterNoOpt(TrackedFaultload faultload) throws IOException {
        testRegister(faultload);
    }

    @FiTest(maxTestCases = 999, optimizeForRetries = true, withPredecessors = false)
    public void testRegister(TrackedFaultload faultload) throws IOException {
        int port = controller.getMappedPort(5000);
        String queryUrl = "http://localhost:" + port + "/v1/faultload/register";

        Request request = new Request.Builder()
                .url(queryUrl)
                .post(body)
                .addHeader("traceparent", faultload.getTraceParent().toString())
                .addHeader("tracestate", faultload.getTraceState().toString())
                .build();

        String inspectUrl = app.controllerInspectUrl + "/v1/trace/" + faultload.getTraceId();
        String traceUrl = "http://localhost:" + app.jaeger.getMappedPort(app.jaegerPort) + "/trace/"
                + faultload.getTraceId();

        try (Response response = client.newCall(request).execute()) {
            TraceAnalysis result = getController().getTrace(faultload);
            boolean containsPersistent = result.getInjectedFaults()
                    .stream()
                    .anyMatch(f -> f.isPersistent() || f.uid().count() >= PROXY_RETRY_COUNT - 1);

            int expectedResponse = containsPersistent ? 500 : 200;
            int actualResponse = response.code();
            assertEquals(expectedResponse, actualResponse);
        }
    }

    @Test
    public void testGlobalFaultload() throws IOException {
        int port = controller.getMappedPort(5000);
        String queryUrl = "http://localhost:" + port + "/v1/faultload/register";

        FaultInjectionPoint proxy1Point = proxy1.getPoint().asAnyCount();
        FaultUid uidProxy1 = FaultUid.anyTo(proxy1Point);
        Fault proxy1Fault = new Fault(uidProxy1, ErrorFault.fromError(HttpError.SERVICE_UNAVAILABLE));

        Faultload faults = new Faultload(Set.of(proxy1Fault));
        app.registerGlobalFaultload(faults);

        Request request = new Request.Builder()
                .url(queryUrl)
                .post(body)
                .addHeader("traceparent", metaFaultload.getTraceParent().toString())
                .addHeader("tracestate", metaFaultload.getTraceState().toString())
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(503, response.code());
        }

        app.unregisterGlobalFaultload();
    }
}
