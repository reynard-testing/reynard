package io.github.delanoflipse.fit.suite.suites.micro;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.delanoflipse.fit.suite.FiTest;
import io.github.delanoflipse.fit.suite.instrument.FaultController;
import io.github.delanoflipse.fit.suite.instrument.InstrumentedApp;
import io.github.delanoflipse.fit.suite.instrument.services.InstrumentedService;
import io.github.delanoflipse.fit.suite.strategy.TrackedFaultload;
import io.github.delanoflipse.fit.suite.strategy.util.Env;
import io.github.delanoflipse.fit.suite.strategy.util.TraceAnalysis;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * FI test the app
 */
@SuppressWarnings("resource")
@Testcontainers(parallel = true)
public class ExampleSuiteIT {
    public static final InstrumentedApp app = new InstrumentedApp().withJaeger();

    private static final String LOG_LEVEL;
    private static final String IMAGE_BUILD_CONTEXT;
    private static final Path IMAGE_PATH;

    private static final ActionComposition action = ActionComposition.Sequential(
            // Call B
            new ServerAction("http://service-b:8080/get",
                    // With a Retry to B
                    new ServerAction("http://service-b:8080/get")),
            // Call C
            new ServerAction("http://service-c:8080/get"),
            // Call F
            new ServerAction("http://service-f:8080/get",
                    // Call H as fallback
                    new ServerAction("http://service-h:8080/get")));
    private static final String actionJson;
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        LOG_LEVEL = Env.getEnv("LOG_LEVEL", "info");
        Path projectRootPath = Path.of("../").toAbsolutePath();
        IMAGE_BUILD_CONTEXT = ".";
        IMAGE_PATH = projectRootPath.resolve("util/micro-benchmarks/services/").toAbsolutePath();
        actionJson = mapper.valueToTree(action).toString();
    }

    @Container
    private static final InstrumentedService serviceB = app.instrument("service-b", 8080, new GenericContainer<>(
            new ImageFromDockerfile()
                    .withFileFromPath(IMAGE_BUILD_CONTEXT, IMAGE_PATH)
                    .withBuildArg("SERVICE_NAME", "leaf"))
            .withEnv("LOG_LEVEL", LOG_LEVEL));

    @Container
    private static final InstrumentedService serviceD = app.instrument("service-d", 8080, new GenericContainer<>(
            new ImageFromDockerfile()
                    .withFileFromPath(IMAGE_BUILD_CONTEXT, IMAGE_PATH)
                    .withBuildArg("SERVICE_NAME", "leaf"))
            .withEnv("LOG_LEVEL", LOG_LEVEL));

    @Container
    private static final InstrumentedService serviceE = app.instrument("service-e", 8080, new GenericContainer<>(
            new ImageFromDockerfile()
                    .withFileFromPath(IMAGE_BUILD_CONTEXT, IMAGE_PATH)
                    .withBuildArg("SERVICE_NAME", "leaf"))
            .withEnv("LOG_LEVEL", LOG_LEVEL));

    @Container
    private static final InstrumentedService serviceC = app.instrument("service-c", 8080, new GenericContainer<>(
            new ImageFromDockerfile()
                    .withFileFromPath(IMAGE_BUILD_CONTEXT, IMAGE_PATH)
                    // .withBuildArg("SERVICE_NAME", "parallel"))
                    .withBuildArg("SERVICE_NAME", "pass-through"))
            .withEnv("LOG_LEVEL", LOG_LEVEL)
            .withEnv("TARGET_SERVICE_URL", "http://service-d:8080/get"));
    // .withEnv("TARGET_SERVICE_URLS",
    // "http://service-d:8080/get,http://service-e:8080/get"));

    @Container
    private static final InstrumentedService serviceF = app.instrument("service-f", 8080, new GenericContainer<>(
            new ImageFromDockerfile()
                    .withFileFromPath(IMAGE_BUILD_CONTEXT, IMAGE_PATH)
                    .withBuildArg("SERVICE_NAME", "pass-through"))
            .withEnv("LOG_LEVEL", LOG_LEVEL)
            .withEnv("TARGET_SERVICE_URL", "http://service-g:8080/get"));

    @Container
    private static final InstrumentedService serviceG = app.instrument("service-g", 8080, new GenericContainer<>(
            new ImageFromDockerfile()
                    .withFileFromPath(IMAGE_BUILD_CONTEXT, IMAGE_PATH)
                    .withBuildArg("SERVICE_NAME", "leaf"))
            .withEnv("LOG_LEVEL", LOG_LEVEL));

    @Container
    private static final InstrumentedService serviceH = app.instrument("service-h", 8080, new GenericContainer<>(
            new ImageFromDockerfile()
                    .withFileFromPath(IMAGE_BUILD_CONTEXT, IMAGE_PATH)
                    .withBuildArg("SERVICE_NAME", "leaf"))
            .withEnv("LOG_LEVEL", LOG_LEVEL));

    @Container
    private static final InstrumentedService serviceA = app.instrument("service-a", 8080, new GenericContainer<>(
            new ImageFromDockerfile()
                    .withFileFromPath(IMAGE_BUILD_CONTEXT, IMAGE_PATH)
                    .withBuildArg("SERVICE_NAME", "complex"))
            .withCopyToContainer(Transferable.of(actionJson), "/action.json")
            .withEnv("LOG_LEVEL", LOG_LEVEL))
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
                .url("http://localhost:" + serviceA.getMappedPort(8080) + "/get")
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
