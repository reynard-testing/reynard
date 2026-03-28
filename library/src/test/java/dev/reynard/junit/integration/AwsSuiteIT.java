package dev.reynard.junit.integration;

import java.io.IOException;

import dev.reynard.junit.FiTest;
import dev.reynard.junit.faultload.modes.ErrorFault;
import dev.reynard.junit.faultload.modes.OmissionFault;
import dev.reynard.junit.instrumentation.FaultController;
import dev.reynard.junit.instrumentation.RemoteController;
import dev.reynard.junit.strategy.TrackedFaultload;
import dev.reynard.junit.strategy.util.Env;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @see util/cdk-test
 */
public class AwsSuiteIT {
    // Note: This are deployment specific.
    public static final String CONTROLLER_HOST = Env.getEnv("CONTROLLER_HOST");
    public static final String BASE_URL = Env.getEnv("BASE_URL");

    public static final RemoteController controller = new RemoteController(CONTROLLER_HOST);
    private OkHttpClient client = new OkHttpClient();

    public static FaultController getController() {
        return controller;
    }

    @FiTest
    public void testApp(TrackedFaultload faultload) throws IOException {
        String queryUrl = BASE_URL;

        Request request = faultload.newRequestBuilder()
                .url(queryUrl)
                .build();

        String inspectUrl = CONTROLLER_HOST + "/v1/trace/" + faultload.getTraceId();

        try (Response response = client.newCall(request).execute()) {
            boolean containsError = faultload.hasFaultMode(ErrorFault.FAULT_TYPE, OmissionFault.FAULT_TYPE);
            int expectedResponse = containsError ? 500 : 200;
            int actualResponse = response.code();
            // assertEquals(expectedResponse, actualResponse);
        }
    }
}
