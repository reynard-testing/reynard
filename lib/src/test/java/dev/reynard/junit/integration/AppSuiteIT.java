package dev.reynard.junit.integration;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * FI test the app
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
                                        .withCommand("go-micro-services geo"))
                        .withHttp2();

        @Container
        private static final InstrumentedService rate = app.instrument("rate", 8080,
                        new GenericContainer<>(BASE_IMAGE)
                                        .withCommand("go-micro-services rate"))
                        .withHttp2();

        @Container
        private static final InstrumentedService search = app.instrument("search", 8080,
                        new GenericContainer<>(BASE_IMAGE)
                                        .withCommand("go-micro-services search"))
                        .withHttp2();

        @Container
        private static final InstrumentedService profile = app.instrument("profile", 8080,
                        new GenericContainer<>(BASE_IMAGE)
                                        .withCommand("go-micro-services profile"))
                        .withHttp2();

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
        public static void setupServices() {
                app.start();
        }

        @AfterAll
        static public void teardownServices() {
                app.stop();
        }

        @FiTest
        public void testApp(TrackedFaultload faultload) throws IOException {
                int frontendPort = frontend.getMappedPort(8080);
                String queryUrl = "http://localhost:" + frontendPort + "/hotels?inDate=2015-04-09&outDate=2015-04-10";

                Request request = new Request.Builder()
                                .url(queryUrl)
                                .addHeader("traceparent", faultload.getTraceParent().toString())
                                .addHeader("tracestate", faultload.getTraceState().toString())
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
