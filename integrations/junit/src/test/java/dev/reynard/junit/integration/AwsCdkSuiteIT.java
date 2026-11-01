package dev.reynard.junit.integration;

import java.io.IOException;

import dev.reynard.junit.FiTest;
import dev.reynard.junit.instrumentation.FaultController;
import dev.reynard.junit.instrumentation.RemoteController;
import dev.reynard.junit.strategy.TrackedFaultload;
import dev.reynard.junit.strategy.util.Env;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Runs against the AWS deployment in util/cdk-test.
 *
 * Requires CONTROLLER_URL and BASE_URL, see the stack outputs.
 */
public class AwsCdkSuiteIT {
    public static final String CONTROLLER_URL = Env.getEnv("CONTROLLER_URL");
    public static final String BASE_URL = Env.getEnv("BASE_URL");

    public static final RemoteController controller = new RemoteController(CONTROLLER_URL);
    private OkHttpClient client = new OkHttpClient();

    public static FaultController getController() {
        return controller;
    }

    @FiTest
    public void testApp(TrackedFaultload faultload) throws IOException {
        Request request = faultload.newRequestBuilder()
                .url(BASE_URL)
                .build();

        // No status assertion: the forwarder passes on the injected status code as-is
        try (Response response = client.newCall(request).execute()) {
        }
    }
}
