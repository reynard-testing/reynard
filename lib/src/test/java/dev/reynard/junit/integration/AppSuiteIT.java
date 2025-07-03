package dev.reynard.junit.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.reynard.junit.FiTest;
import dev.reynard.junit.faultload.modes.ErrorFault;
import dev.reynard.junit.faultload.modes.OmissionFault;
import dev.reynard.junit.instrumentation.FaultController;
import dev.reynard.junit.instrumentation.testcontainers.InstrumentedApp;
import dev.reynard.junit.instrumentation.testcontainers.services.InstrumentedService;
import dev.reynard.junit.strategy.TrackedFaultload;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This is an older test suite for
 * https://github.com/delanoflipse/go-micro-services-otel
 * 
 * It requires the BASE_IMAGE to be prebuilt
 */
@SuppressWarnings("resource")
@Testcontainers(parallel = true)
public class AppSuiteIT {
    public static final InstrumentedApp app = new InstrumentedApp().withJaeger();
    private static final String BASE_IMAGE = "go-micro-service:latest";
    private OkHttpClient client = new OkHttpClient();

    @Container
    private static final InstrumentedService geo = app.instrument("geo", 8080,
            new GenericContainer<>(BASE_IMAGE)
                    .withCommand("go-micro-services geo"));

    @Container
    private static final InstrumentedService rate = app.instrument("rate", 8080,
            new GenericContainer<>(BASE_IMAGE)
                    .withCommand("go-micro-services rate"));

    @Container
    private static final InstrumentedService search = app.instrument("search", 8080,
            new GenericContainer<>(BASE_IMAGE)
                    .withCommand("go-micro-services search"));

    @Container
    private static final InstrumentedService profile = app.instrument("profile", 8080,
            new GenericContainer<>(BASE_IMAGE)
                    .withCommand("go-micro-services profile"));

    @Container
    private static final GenericContainer<?> frontend = new GenericContainer<>(BASE_IMAGE)
            .withNetwork(app.network)
            .withCommand("go-micro-services frontend")
            .withExposedPorts(8080)
            .dependsOn(search.getService(), profile.getService());

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

    @FiTest
    public void testApp(TrackedFaultload faultload) throws IOException {
        int frontendPort = frontend.getMappedPort(8080);
        String queryUrl = "http://localhost:" + frontendPort + "/hotels?inDate=2015-04-09&outDate=2015-04-10";

        Request request = faultload.newRequestBuilder()
                .url(queryUrl)
                .build();

        String inspectUrl = app.controllerInspectUrl + "/v1/trace/" + faultload.getTraceId();
        String traceUrl = "http://localhost:" + app.jaeger.getMappedPort(app.jaegerPort) + "/trace/"
                + faultload.getTraceId();

        try (Response response = client.newCall(request).execute()) {
            boolean containsError = faultload.hasFaultMode(ErrorFault.FAULT_TYPE, OmissionFault.FAULT_TYPE);
            int expectedResponse = containsError ? 500 : 200;
            int actualResponse = response.code();
            assertEquals(expectedResponse, actualResponse);

            boolean allRunning = app.allRunning();
            assertTrue(allRunning);
        }
    }
}
