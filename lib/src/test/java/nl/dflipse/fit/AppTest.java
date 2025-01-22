package nl.dflipse.fit;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import nl.dflipse.fit.instrument.InstrumentedApp;
import nl.dflipse.fit.strategy.Faultload;
import nl.dflipse.fit.strategy.FiTest;
import nl.dflipse.fit.strategy.InstrumentedTest;

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
public class AppTest implements InstrumentedTest {

    static private InstrumentedApp app;

    @SuppressWarnings("resource")
    @BeforeAll
    static public void setupServices() {
        app = new InstrumentedApp();

        // Add services
        String baseImage = "go-micro-service:latest";

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

        GenericContainer<?> frontend = new GenericContainer<>(baseImage)
                .withCommand("go-micro-services frontend")
                .withExposedPorts(8080)
                .dependsOn(search, profile);

        app.addService("frontend", frontend);

        GenericContainer<?> jaeger = new GenericContainer<>("jaegertracing/all-in-one:latest")
                .withExposedPorts(16686);
        app.addService("jaeger", jaeger);

        MountableFile otelCollectorConfig = MountableFile.forHostPath("../../otel-collector-config.yaml");
        GenericContainer<?> otelCollector = new GenericContainer<>(
                "otel/opentelemetry-collector-contrib:latest")
                .withCopyFileToContainer(otelCollectorConfig, "/otel-collector-config.yaml")
                .withCommand("--config=/otel-collector-config.yaml")
                .dependsOn(app.collector.getContainer(), jaeger);
        app.addService("otel-collector", otelCollector);

        // Start services
        app.start();
    }

    @AfterAll
    static public void teardownServices() {
        app.stop();
    }

    @Override
    public InstrumentedApp getApp() {
        return app;
    }

    @FiTest
    public void testApp(Faultload faultload) throws IOException {
        int frontendPort = app.getContainerByName("frontend").getMappedPort(8080);
        String queryUrl = "http://localhost:" + frontendPort + "/hotels?inDate=2015-04-09&outDate=2015-04-10";

        Response res = Request.get(queryUrl)
                .addHeader("traceparent", faultload.getTraceParent().toString())
                .addHeader("tracestate", faultload.getTraceState().toString())
                .execute();

        String inspectUrl = app.collectorInspectUrl + "/v1/get/" + faultload.getTraceId();
        int expectedResponse = faultload.size() > 0 ? 500 : 200;
        int actualResponse = res.returnResponse().getCode();
        assertEquals(expectedResponse, actualResponse);

        boolean allRunning = app.allRunning();
        assertTrue(allRunning);
    }
}
