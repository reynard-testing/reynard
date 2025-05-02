package io.github.delanoflipse.fit.suite.suites;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

import io.github.delanoflipse.fit.suite.FiTest;
import io.github.delanoflipse.fit.suite.instrument.FaultController;
import io.github.delanoflipse.fit.suite.instrument.controller.RemoteController;
import io.github.delanoflipse.fit.suite.strategy.TrackedFaultload;
import io.github.delanoflipse.fit.suite.strategy.util.TraceAnalysis;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * FI test the app
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
        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + SERVICE_PORT + "/users/user1/books/book2")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
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

    @FiTest(maskPayload = true, maxTestCases = 9999, withCallStack = true)
    public void testNetflix(TrackedFaultload faultload) throws IOException {

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + SERVICE_PORT + "/netflix/homepage/users/chris_rivers")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
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

    @FiTest(maxTestCases = 99)
    public void testCinema1(TrackedFaultload faultload) throws IOException {

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
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

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 100)
    public void testCinema3(TrackedFaultload faultload) throws IOException {

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
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

    @FiTest(maxTestCases = 500)
    public void testCinema4(TrackedFaultload faultload) throws IOException {

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 500)
    public void testCinema5(TrackedFaultload faultload) throws IOException {

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 500)
    public void testCinema6(TrackedFaultload faultload) throws IOException {

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 500)
    public void testCinema7(TrackedFaultload faultload) throws IOException {

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + SERVICE_PORT + "/users/chris_rivers/bookings")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 500)
    public void testCinema8(TrackedFaultload faultload) throws IOException {

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + 5001 + "/users/chris_rivers/bookings")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
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

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + SERVICE_PORT + "/review/hotels/hotel1")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }

    @FiTest(maxTestCases = 1000)
    public void testMailchimp(TrackedFaultload faultload) throws IOException {

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + SERVICE_PORT + "/urls/randomurl")
                // .url("http://localhost:" + SERVICE_PORT + "/urls/prettyurl")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
        }
    }
}
