package nl.dflipse.fit.suites;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

import nl.dflipse.fit.FiTest;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.OmissionFault;
import nl.dflipse.fit.instrument.FaultController;
import nl.dflipse.fit.instrument.controller.RemoteController;
import nl.dflipse.fit.strategy.TrackedFaultload;
import nl.dflipse.fit.strategy.util.TraceAnalysis;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * FI test the app
 */
public class FilibusterSuiteIT {
    private static final RemoteController controller = new RemoteController("http://localhost:6050");

    private static final int AUDIBLE_PORT = 5000;
    private static final int NETFLIX_PORT = 5000;
    private static final int CINEMA_PORT = 5000;

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
                .url("http://localhost:" + AUDIBLE_PORT + "/users/user1/books/book2")
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

    @FiTest(maskPayload = true, maxTestCases = 9999, optimizeForImpactless = false)
    public void testNetflix(TrackedFaultload faultload) throws IOException {

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + NETFLIX_PORT + "/netflix/homepage/users/chris_rivers")
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
                .url("http://localhost:" + CINEMA_PORT + "/users/chris_rivers/bookings")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String inspectUrl = controller.apiHost + "/v1/trace/" + faultload.getTraceId();
            String traceUrl = "http://localhost:8686/trace/" + faultload.getTraceId();

            TraceAnalysis result = getController().getTrace(faultload);
            boolean injectedFaults = !result.getInjectedFaults().isEmpty();

            if (injectedFaults) {
                assert (response.code() >= 500);
                assert (response.code() < 600);
            } else {
                assertEquals(200, response.code());
            }
        }
    }

    @FiTest(maxTestCases = 500)
    public void testCinema3(TrackedFaultload faultload) throws IOException {

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + CINEMA_PORT + "/users/chris_rivers/bookings")
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
}
