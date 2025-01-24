package nl.dflipse.fit;

import org.testcontainers.containers.GenericContainer;

import nl.dflipse.fit.instrument.InstrumentedApp;
import nl.dflipse.fit.strategy.Faultload;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.client5.http.fluent.Request;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * FI test the app
 */
public class AppTest {
    public static InstrumentedApp app;

    @SuppressWarnings("resource")
    @BeforeAll
    static public void setupServices() {
        app = new InstrumentedApp();

        // Add services
        String baseImage = "go-micro-service:latest";

        // Instrumented services
        GenericContainer<?> geo = new GenericContainer<>(baseImage)
                .withCommand("go-micro-services geo");
        app.addInstrumentedService("geo", geo, 8080)
                .withHttp2();

        GenericContainer<?> rate = new GenericContainer<>(baseImage)
                .withCommand("go-micro-services rate");
        app.addInstrumentedService("rate", rate, 8080)
                .withHttp2();

        GenericContainer<?> search = new GenericContainer<>(baseImage)
                .withCommand("go-micro-services search")
                .dependsOn(geo, rate);
        app.addInstrumentedService("search", search, 8080)
                .withHttp2();

        GenericContainer<?> profile = new GenericContainer<>(baseImage)
                .withCommand("go-micro-services profile")
                .dependsOn(geo, rate);

        app.addInstrumentedService("profile", profile, 8080)
                .withHttp2();

        // Main client, the frontend service
        GenericContainer<?> frontend = new GenericContainer<>(baseImage)
                .withCommand("go-micro-services frontend")
                .withExposedPorts(8080)
                .dependsOn(search, profile);

        app.addService("frontend", frontend);

        // Collect traces in Jaeger for inspection
        GenericContainer<?> jaeger = new GenericContainer<>("jaegertracing/all-in-one:latest")
                .withExposedPorts(16686);

        app.collector.getContainer().dependsOn(jaeger);
        app.addService("jaeger", jaeger);

        // Start services
        app.start();
    }

    @AfterAll
    static public void teardownServices() {
        app.stop();
    }

    @FiTest
    public void testApp(Faultload faultload) throws IOException {
        int frontendPort = app.getContainerByName("frontend").getMappedPort(8080);
        String queryUrl = "http://localhost:" + frontendPort + "/hotels?inDate=2015-04-09&outDate=2015-04-10";

        Response res = Request.get(queryUrl)
                .addHeader("traceparent", faultload.getTraceParent().toString())
                .addHeader("tracestate", faultload.getTraceState().toString())
                .execute();

        String inspectUrl = app.orchestratorInspectUrl + "/v1/get/" + faultload.getTraceId();

        boolean containsError = faultload.getFaultload().stream()
                .anyMatch(f -> f.faultMode.getType().equals("HTTP_ERROR"));
        int expectedResponse = containsError ? 500 : 200;
        int actualResponse = res.returnResponse().getCode();
        assertEquals(expectedResponse, actualResponse);

        boolean allRunning = app.allRunning();
        assertTrue(allRunning);
    }
}
