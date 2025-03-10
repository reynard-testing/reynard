package nl.dflipse.fit.instrument.controller;

import java.io.IOException;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.ContentType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.dflipse.fit.instrument.FaultController;
import nl.dflipse.fit.strategy.TrackedFaultload;
import nl.dflipse.fit.strategy.util.TraceAnalysis;

public class RemoteController implements FaultController {

    public String apiHost;

    public RemoteController(String apiHost) {
        this.apiHost = apiHost;
    }

    public RemoteController() {
        this.apiHost = null;
    }

    private TraceAnalysis attemptToGetTrace(TrackedFaultload faultload) throws IOException {
        String queryUrl = apiHost + "/v1/trace/" + faultload.getTraceId();
        Response res = Request.get(queryUrl).execute();
        String body = res.returnContent().asString();
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

    @Override
    public TraceAnalysis getTrace(TrackedFaultload faultload) throws IOException {
        if (apiHost == null) {
            throw new IllegalStateException("Collector URL not set");
        }

        int maxRetries = 5;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return attemptToGetTrace(faultload);
            } catch (IOException e) {
                if (attempt == maxRetries - 1) {
                    throw e;
                }
            }

            try {
                int backoff = 1000 * (int) Math.pow(2, attempt);
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        throw new IOException("Failed to get trace after " + maxRetries + " attempts");
    }

    @Override
    public void registerFaultload(TrackedFaultload faultload) {
        if (apiHost == null) {
            throw new IllegalStateException("Collector URL not set");
        }

        String queryUrl = apiHost + "/v1/faultload/register";

        try {
            String jsonBody = faultload.serializeJson();

            Response res = Request.post(queryUrl)
                    .bodyString(jsonBody, ContentType.APPLICATION_JSON)
                    .execute();
            var resBody = res.returnContent().asString(); // Ensure the request is executed
            if (!resBody.equals("OK")) {
                throw new IOException("Failed to register faultload: " + resBody);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void unregisterFaultload(TrackedFaultload faultload) {
        if (apiHost == null) {
            throw new IllegalStateException("Collector URL not set");
        }

        String queryUrl = apiHost + "/v1/faultload/unregister";
        ObjectMapper mapper = new ObjectMapper();
        var node = mapper.createObjectNode();
        node.put("trace_id", faultload.getTraceId());

        try {
            String jsonBody = node.toString();

            Response res = Request.post(queryUrl)
                    .bodyString(jsonBody, ContentType.APPLICATION_JSON)
                    .execute();
            var resBody = res.returnContent().asString(); // Ensure the request is executed
            if (!resBody.equals("OK")) {
                throw new IOException("Failed to unregister faultload: " + resBody);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
