package dev.reynard.junit.integration;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import dev.reynard.junit.FiTest;
import dev.reynard.junit.instrumentation.FaultController;
import dev.reynard.junit.instrumentation.RemoteController;
import dev.reynard.junit.strategy.TrackedFaultload;
import dev.reynard.junit.strategy.util.TraceAnalysis;
import dev.reynard.junit.strategy.util.traversal.TraversalOrder;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * The Filibuster test suite.
 * 
 * Please run each test individually, as they are not designed to be run all at
 * once.
 */
public class FilibusterSuiteIT {
    private static final RemoteController controller = new RemoteController("http://localhost:6050");

    private static final int SERVICE_PORT = 5000;

    private OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    public static final MediaType JSON = MediaType.get("application/json");

    public static FaultController getController() {
        return controller;
    }

    @FiTest()
    public void testAudible(TrackedFaultload faultload) throws IOException {

        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + SERVICE_PORT + "/users/user1/books/book2")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String inspectUrl = controller.apiHost + "/v1/trace/" + faultload.getTraceId();
            String traceUrl = "http://localhost:8686/trace/" + faultload.getTraceId();

            TraceAnalysis result = getController().getTrace(faultload);
            // boolean injectedFaults = result.getInjectedFaults();

            // if (containsPersistent) {
            // assert (response.code() >= 500);
            // assert (response.code() < 600);
            // } else {
            // assertEquals(200, response.code());
            // }
        }
    }

    @FiTest()
    public void testAudibleFaults(TrackedFaultload faultload) throws IOException {
        testAudible(faultload);
    }

    @FiTest(maxTestCases = 999, pointOrder = TraversalOrder.BREADTH_FIRST)
    public void testAudibleBfs(TrackedFaultload faultload) throws IOException {
        testAudible(faultload);
    }

    @FiTest(maxTestCases = 999, pointOrder = TraversalOrder.RANDOM)
    public void testAudibleRandomOrder(TrackedFaultload faultload) throws IOException {
        testAudible(faultload);
    }

    @FiTest(maxTestCases = 999, withPredecessors = true)
    public void testAudibleCs(TrackedFaultload faultload) throws IOException {
        testAudible(faultload);
    }

    @FiTest(maskPayload = true, maxTestCases = 9999, withPredecessors = true)
    public void testNetflix(TrackedFaultload faultload) throws IOException {

        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + SERVICE_PORT + "/netflix/homepage/users/chris_rivers")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String inspectUrl = controller.apiHost + "/v1/trace/" + faultload.getTraceId();
            String traceUrl = "http://localhost:8686/trace/" + faultload.getTraceId();

            TraceAnalysis result = getController().getTrace(faultload);
            // boolean injectedFaults = result.getInjectedFaults();

            // if (containsPersistent) {
            // assert (response.code() >= 500);
            // assert (response.code() < 600);
            // } else {
            // assertEquals(200, response.code());
            // }
        }
    }

    @FiTest(maskPayload = true, maxTestCases = 9999)
    public void testNetflixNoCs(TrackedFaultload faultload) throws IOException {
        testNetflix(faultload);
    }

    @FiTest(maskPayload = true, maxTestCases = 9999, withPredecessors = true, pointOrder = TraversalOrder.BREADTH_FIRST)
    public void testNetflixBfs(TrackedFaultload faultload) throws IOException {
        testNetflix(faultload);
    }

    @FiTest(maskPayload = true, maxTestCases = 9999, withPredecessors = true, pointOrder = TraversalOrder.RANDOM)
    public void testNetflixRandomOrder(TrackedFaultload faultload) throws IOException {
        testNetflix(faultload);
    }

    @FiTest(maskPayload = true, maxTestCases = 9999, withPredecessors = true)
    public void testNetflixFaults(TrackedFaultload faultload) throws IOException {
        testNetflix(faultload);
    }

    @FiTest(maxTestCases = 99)
    public void testCinema1(TrackedFaultload faultload) throws IOException {

        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String inspectUrl = controller.apiHost + "/v1/trace/" + faultload.getTraceId();
            String traceUrl = "http://localhost:8686/trace/" + faultload.getTraceId();

            TraceAnalysis result = getController().getTrace(faultload);
            boolean injectedFaults = !result.getInjectedFaults().isEmpty();

            // if (injectedFaults) {
            // assert (response.code() >= 500);
            // assert (response.code() < 600);
            // } else {
            // assertEquals(200, response.code());
            // }
        }
    }

    @FiTest(maxTestCases = 500)
    public void testCinema2(TrackedFaultload faultload) throws IOException {

        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 100)
    public void testCinema3(TrackedFaultload faultload) throws IOException {

        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String inspectUrl = controller.apiHost + "/v1/trace/" + faultload.getTraceId();
            String traceUrl = "http://localhost:8686/trace/" + faultload.getTraceId();

            TraceAnalysis result = getController().getTrace(faultload);
            boolean injectedFaults = !result.getInjectedFaults().isEmpty();

            // if (injectedFaults) {
            // assert (response.code() >= 500);
            // assert (response.code() < 600);
            // } else {
            // assertEquals(200, response.code());
            // }
        }
    }

    @FiTest(maxTestCases = 500, optimizeForRetries = true)
    public void testCinema3Retries(TrackedFaultload faultload) throws IOException {
        testCinema3(faultload);
    }

    @FiTest(maxTestCases = 500, pointOrder = TraversalOrder.DEPTH_FIRST_REVERSE_POST_ORDER)
    public void testCinema3DfsRevPostOrder(TrackedFaultload faultload) throws IOException {
        testCinema3(faultload);
    }

    @FiTest(maxTestCases = 500)
    public void testCinema4(TrackedFaultload faultload) throws IOException {

        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 500)
    public void testCinema5(TrackedFaultload faultload) throws IOException {

        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 500)
    public void testCinema6(TrackedFaultload faultload) throws IOException {

        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 500)
    public void testCinema7(TrackedFaultload faultload) throws IOException {

        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 500)
    public void testCinema8(TrackedFaultload faultload) throws IOException {

        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + 5001 + "/users/chris_rivers/bookings")
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 500, optimizeForRetries = true)
    public void testCinema8Retries(TrackedFaultload faultload) throws IOException {
        testCinema8(faultload);
    }

    @FiTest(maxTestCases = 500)
    public void testExpedia(TrackedFaultload faultload) throws IOException {

        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + SERVICE_PORT + "/review/hotels/hotel1")
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 500)
    public void testExpediaWithAssertions(TrackedFaultload faultload) throws IOException {

        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + SERVICE_PORT + "/review/hotels/hotel1")
                .build();

        try (Response response = client.newCall(request).execute()) {
            TraceAnalysis result = getController().getTrace(faultload);
            boolean hasInjectionAtMl = result.getInjectedFaults()
                    .stream()
                    .anyMatch(f -> f.uid().destination().equals("review-ml"));

            boolean hasReqToTime = result.getFaultUids()
                    .stream()
                    .anyMatch(f -> f.destination().equals("review-time"));

            assertTrue((hasInjectionAtMl && hasReqToTime) || (!hasInjectionAtMl && !hasReqToTime));
        }
    }

    @FiTest(maxTestCases = 1000)
    public void testMailchimp(TrackedFaultload faultload) throws IOException {

        Request request = faultload.newRequestBuilder()
                .url("http://localhost:" + SERVICE_PORT + "/urls/randomurl")
                // .url("http://localhost:" + SERVICE_PORT + "/urls/prettyurl")
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 1000)
    public void testMailchimpFaults(TrackedFaultload faultload) throws IOException {
        testMailchimp(faultload);
    }
}
