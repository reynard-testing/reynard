package nl.dflipse.fit.instrument.controller;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.dflipse.fit.instrument.FaultController;
import nl.dflipse.fit.strategy.TrackedFaultload;
import nl.dflipse.fit.strategy.util.TraceAnalysis;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RemoteController implements FaultController {
    private final Logger logger = LoggerFactory.getLogger(RemoteController.class);

    public String apiHost;
    private final LRUCache<String, TraceAnalysis> traceCache = new LRUCache<>(3);

    private OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    public static final MediaType JSON = MediaType.get("application/json");

    public RemoteController(String apiHost) {
        this.apiHost = apiHost;
    }

    public RemoteController() {
        this.apiHost = null;
    }

    private TraceAnalysis attemptToGetTrace(TrackedFaultload faultload) throws IOException {
        String queryUrl = apiHost + "/v1/trace/" + faultload.getTraceId();
        Request request = new Request.Builder()
                // .addHeader("Content-Type", "application/json")
                .url(queryUrl)
                .build();

        try (Response httpRes = client.newCall(request).execute()) {
            String body = httpRes.body().string();
            ControllerResponse response = new ObjectMapper().readValue(body,
                    new TypeReference<ControllerResponse>() {
                    });

            if (response.trees.isEmpty()) {
                throw new IOException("Empty trace tree found for traceId: " + faultload.getTraceId());
            }

            if (response.trees.size() > 1) {
                throw new IOException("Trace is not fully connected for traceId: " + faultload.getTraceId());
            }

            var traceTreeRoot = response.trees.get(0);
            var traceReports = response.reports;

            TraceAnalysis trace = new TraceAnalysis(traceTreeRoot, traceReports);

            var rootSpanId = traceTreeRoot.span.spanId;
            var expectedRoot = faultload.getTraceParent().parentSpanId;
            if (!rootSpanId.equals(expectedRoot)) {
                throw new IOException("Root span mismatch: " + rootSpanId + " != " + expectedRoot);
            }

            if (trace.isIncomplete()) {
                throw new IOException("Trace is incomplete");
            }

            return trace;
        }
    }

    @Override
    public TraceAnalysis getTrace(TrackedFaultload faultload) throws IOException {
        if (apiHost == null) {
            throw new IllegalStateException("Collector URL not set");
        }

        if (traceCache.containsKey(faultload.getTraceId())) {
            return traceCache.get(faultload.getTraceId());
        }

        faultload.timer.start("getTraceWithDelay");
        if (faultload.getDelayMs > 0) {
            try {
                Thread.sleep(faultload.getDelayMs);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        faultload.timer.start("getTrace");
        int maxRetries = 7;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                var traceData = attemptToGetTrace(faultload);
                traceCache.put(faultload.getTraceId(), traceData);
                faultload.timer.stop("getTrace");
                return traceData;
            } catch (IOException e) {
                if (attempt == maxRetries - 1) {
                    throw e;
                }

                logger.debug("Retrying getting trace due to: {}", e.getMessage());
            }

            try {
                int backoff = 250 * (int) Math.pow(2, attempt);
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        faultload.timer.stop("getTrace");
        faultload.timer.stop("getTraceWithDelay");
        throw new IOException("Failed to get trace after " + maxRetries + " attempts");
    }

    @Override
    public void registerFaultload(TrackedFaultload faultload) throws IOException {
        if (apiHost == null) {
            throw new IllegalStateException("Collector URL not set");
        }

        String queryUrl = apiHost + "/v1/faultload/register";
        String jsonBody = faultload.serializeJson();
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = new Request.Builder()
                // .addHeader("Content-Type", "application/json")
                .url(queryUrl)
                .post(body)
                .build();

        try (Response httpRes = client.newCall(request).execute()) {
            if (!httpRes.isSuccessful()) {
                throw new IOException("Failed to register faultload: " + httpRes.body().string());
            }

            String resBody = httpRes.body().string(); // Ensure the request is executed

            if (!resBody.equals("OK")) {
                throw new IOException("Failed to register faultload: " + resBody);
            }
        }
    }

    @Override
    public void unregisterFaultload(TrackedFaultload faultload) throws IOException {
        if (apiHost == null) {
            throw new IllegalStateException("Collector URL not set");
        }

        String queryUrl = apiHost + "/v1/faultload/unregister";
        ObjectMapper mapper = new ObjectMapper();
        var node = mapper.createObjectNode();
        node.put("trace_id", faultload.getTraceId());

        String jsonBody = node.toString();
        ;
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = new Request.Builder()
                // .addHeader("Content-Type", "application/json")
                .url(queryUrl)
                .post(body)
                .build();

        try (Response httpRes = client.newCall(request).execute()) {
            if (!httpRes.isSuccessful()) {
                throw new IOException("Failed to register faultload: " + httpRes.body().string());
            }

            String resBody = httpRes.body().string(); // Ensure the request is executed

            if (!resBody.equals("OK")) {
                throw new IOException("Failed to unregister faultload: " + resBody);
            }
        }
    }
}
