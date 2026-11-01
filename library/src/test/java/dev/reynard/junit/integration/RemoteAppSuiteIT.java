package dev.reynard.junit.integration;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import dev.reynard.junit.FiTest;
import dev.reynard.junit.faultload.modes.ErrorFault;
import dev.reynard.junit.faultload.modes.OmissionFault;
import dev.reynard.junit.instrumentation.FaultController;
import dev.reynard.junit.instrumentation.RemoteController;
import dev.reynard.junit.strategy.TrackedFaultload;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This is an older test suite for
 * https://github.com/delanoflipse/go-micro-services-otel
 * 
 * It requires the project to be running on localhost:5000
 * (this is the remote version of AppSuiteIT)
 */
public class RemoteAppSuiteIT {
    public static final RemoteController controller = new RemoteController("http://localhost:5000");
    private OkHttpClient client = new OkHttpClient();

    public static FaultController getController() {
        return controller;
    }

    @FiTest
    public void testApp(TrackedFaultload faultload) throws IOException {
        int frontendPort = 8080;
        String queryUrl = "http://localhost:" + frontendPort + "/hotels?inDate=2015-04-09&outDate=2015-04-10";

        Request request = faultload.newRequestBuilder()
                .url(queryUrl)
                .build();

        String inspectUrl = "http://localhost:5000/v1/trace/" + faultload.getTraceId();
        String traceUrl = "http://localhost:16686/trace/"
                + faultload.getTraceId();

        try (Response response = client.newCall(request).execute()) {
            boolean containsError = faultload.hasFaultMode(ErrorFault.FAULT_TYPE, OmissionFault.FAULT_TYPE);
            int expectedResponse = containsError ? 500 : 200;
            int actualResponse = response.code();
            assertEquals(expectedResponse, actualResponse);
        }
    }
}
