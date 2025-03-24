package nl.dflipse.fit;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.Assert.assertEquals;

import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.OmissionFault;
import nl.dflipse.fit.instrument.FaultController;
import nl.dflipse.fit.instrument.InstrumentedApp;
import nl.dflipse.fit.instrument.services.InstrumentedService;
import nl.dflipse.fit.strategy.TrackedFaultload;
import nl.dflipse.fit.strategy.util.TraceAnalysis;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@SuppressWarnings("resource")
@Testcontainers(parallel = true)
public class MetaTest {
    public static final InstrumentedApp app = new InstrumentedApp().withJaeger();
    private static final String PROXY_IMAGE = "fit-proxy:latest";
    private static final String COORDINATOR_IMAGE = "fit-otel-orchestrator:latest";
    private static final String COLLECTOR_ENDPOINT = "http://" + app.collectorHost + ":4317";
    public static final MediaType JSON = MediaType.get("application/json");

    private OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .build();
    private final static TrackedFaultload meta = new TrackedFaultload();

    @Container
    private static final InstrumentedService proxy1 = app.instrument("proxy1", 8050,
            new GenericContainer<>(PROXY_IMAGE)
                    .withEnv("PROXY_HOST", "0.0.0.0:8080")
                    .withEnv("PROXY_TARGET", "http://0.0.0.0:8090")
                    .withEnv("SERVICE_NAME", "proxy1")
                    .withEnv("ORCHESTRATOR_HOST", "coordinator:5000")
                    .withEnv("CONTROLLER_PORT", "8050")
                    .withEnv("USE_OTEL", "true")
                    .withEnv("OTEL_SERVICE_NAME", "proxy1")
                    .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", COLLECTOR_ENDPOINT));

    @Container
    private static final InstrumentedService proxy2 = app.instrument("proxy2", 8050,
            new GenericContainer<>(PROXY_IMAGE)
                    .withEnv("PROXY_HOST", "0.0.0.0:8080")
                    .withEnv("PROXY_TARGET", "http://0.0.0.0:8090")
                    .withEnv("SERVICE_NAME", "proxy2")
                    .withEnv("ORCHESTRATOR_HOST", "coordinator:5000")
                    .withEnv("CONTROLLER_PORT", "8050")
                    .withEnv("USE_OTEL", "true")
                    .withEnv("OTEL_SERVICE_NAME", "proxy2")
                    .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", COLLECTOR_ENDPOINT));

    @Container
    private static final InstrumentedService proxy3 = app.instrument("proxy3", 8050,
            new GenericContainer<>(PROXY_IMAGE)
                    .withEnv("PROXY_HOST", "0.0.0.0:8080")
                    .withEnv("PROXY_TARGET", "http://0.0.0.0:8090")
                    .withEnv("SERVICE_NAME", "proxy3")
                    .withEnv("ORCHESTRATOR_HOST", "coordinator:5000")
                    .withEnv("CONTROLLER_PORT", "8050")
                    .withEnv("USE_OTEL", "true")
                    .withEnv("OTEL_SERVICE_NAME", "proxy3")
                    .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", COLLECTOR_ENDPOINT));

    @Container
    private static final InstrumentedService orchestrator = app.instrument("coordinator", 5000,
            new GenericContainer<>(COORDINATOR_IMAGE)
                    .withEnv("PROXY_LIST", "proxy1:8050,proxy2:8050,proxy3:8050")
                    .withEnv("OTEL_SERVICE_NAME", "orchestrator")
                    .withEnv("OTEL_TRACES_EXPORTER", "otlp")
                    .withEnv("OTEL_BSP_SCHEDULE_DELAY", "1")
                    .withEnv("OTEL_BSP_EXPORT_TIMEOUT", "100")
                    .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", COLLECTOR_ENDPOINT)
                    .withExposedPorts(5000));

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

    @FiTest(getTraceInitialDelay = 200)
    public void testRegister(TrackedFaultload faultload) throws IOException {
        int port = orchestrator.getService().getMappedPort(5000);
        String queryUrl = "http://localhost:" + port + "/v1/faultload/register";

        String jsonBody = meta.serializeJson();
        RequestBody body = RequestBody.create(jsonBody, JSON);

        Request request = new Request.Builder()
                .url(queryUrl)
                .post(body)
                .addHeader("traceparent", faultload.getTraceParent().toString())
                .addHeader("tracestate", faultload.getTraceState().toString())
                .build();

        String inspectUrl = app.orchestratorInspectUrl + "/v1/trace/" + faultload.getTraceId();
        String traceUrl = "http://localhost:" + app.jaeger.getMappedPort(app.jaegerPort) + "/trace/"
                + faultload.getTraceId();

        try (Response response = client.newCall(request).execute()) {
            TraceAnalysis result = getController().getTrace(faultload);
            boolean containsPersistent = result.getInjectedFaults()
                    .stream()
                    .anyMatch(f -> f.isPersistent());

            int expectedResponse = containsPersistent ? 500 : 200;
            int actualResponse = response.code();
            assertEquals(expectedResponse, actualResponse);
        }
    }
}
