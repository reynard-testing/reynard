package dev.reynard.junit.integration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;

import dev.reynard.junit.FiTest;
import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.faultload.Faultload;
import dev.reynard.junit.faultload.modes.ErrorFault;
import dev.reynard.junit.faultload.modes.OmissionFault;
import dev.reynard.junit.instrumentation.FaultController;
import dev.reynard.junit.instrumentation.RemoteController;
import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.TrackedFaultload;
import dev.reynard.junit.strategy.components.FeedbackContext;
import dev.reynard.junit.strategy.components.FeedbackHandler;
import dev.reynard.junit.strategy.components.PruneContext;
import dev.reynard.junit.strategy.components.PruneDecision;
import dev.reynard.junit.strategy.components.Pruner;
import dev.reynard.junit.strategy.util.TraceAnalysis;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * FI test the app
 */
public class HotelReservationSuiteIT {
    private static final RemoteController controller = new RemoteController("http://localhost:6116");

    private static final int PORT = 5000;

    private OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    public static final MediaType JSON = MediaType.get("application/json");

    public static FaultController getController() {
        return controller;
    }

    // https://github.com/delimitrou/DeathStarBench/blob/master/hotelReservation/wrk2/scripts/hotel-reservation/mixed-workload_type_1.lua
    @FiTest(hashBody = false, logHeaders = true)
    public void testGetHotels(TrackedFaultload faultload) throws IOException {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("http")
                .host("localhost")
                .port(PORT)
                .addPathSegments("hotels")
                .addQueryParameter("inDate", "2015-04-09")
                .addQueryParameter("outDate", "2015-04-10")
                .addQueryParameter("lat", "37.7749")
                .addQueryParameter("lon", "-122.4194")
                .build();

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String inspectUrl = controller.apiHost + "/v1/trace/" + faultload.getTraceId();
            String traceUrl = "http://localhost:16686/trace/" + faultload.getTraceId();

            TraceAnalysis result = getController().getTrace(faultload);
        }
    }
}
