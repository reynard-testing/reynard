package io.github.delanoflipse.fit.suite.suites;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.github.delanoflipse.fit.suite.FiTest;
import io.github.delanoflipse.fit.suite.instrument.FaultController;
import io.github.delanoflipse.fit.suite.instrument.controller.RemoteController;
import io.github.delanoflipse.fit.suite.strategy.TrackedFaultload;
import io.github.delanoflipse.fit.suite.strategy.util.TraceAnalysis;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * FI test the app
 */
public class MicroSuiteIT {
    private static final RemoteController controller = new RemoteController("http://localhost:5050");

    private static final int SERVICE_PORT = 8080;

    private OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public static FaultController getController() {
        return controller;
    }

    @FiTest()
    public void testA(TrackedFaultload faultload) throws IOException {
        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url("http://localhost:" + SERVICE_PORT + "/get")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String inspectUrl = controller.apiHost + "/v1/trace/" + faultload.getTraceId();

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

}
