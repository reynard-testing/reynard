package nl.dflipse.fit.instrument.controller;

import java.io.IOException;
import java.util.List;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.ContentType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.instrument.FaultController;
import nl.dflipse.fit.trace.tree.TraceTreeSpan;

public class RemoteController implements FaultController {

    public String collectorUrl;

    public RemoteController(String collectorUrl) {
        this.collectorUrl = collectorUrl;
    }

    private TraceTreeSpan attemptToGetTrace(Faultload faultload) throws IOException {
        String queryUrl = collectorUrl + "/v1/get-trees/" + faultload.getTraceId();
        Response res = Request.get(queryUrl).execute();
        String body = res.returnContent().asString();
        List<TraceTreeSpan> orchestratorResponse = new ObjectMapper().readValue(body,
                new TypeReference<List<TraceTreeSpan>>() {
                });

        if (orchestratorResponse.isEmpty()) {
            throw new IOException("Empty trace tree found for traceId: " + faultload.getTraceId());
        }

        if (orchestratorResponse.size() > 1) {
            throw new IOException("Trace is not fully connected for traceId: " + faultload.getTraceId());
        }

        var trace = orchestratorResponse.get(0);

        var rootSpanId = trace.span.spanId;
        var expectedRoot = faultload.getTraceParent().parentSpanId;
        if (!rootSpanId.equals(expectedRoot)) {
            throw new IOException("Root span mismatch: " + rootSpanId + " != " + expectedRoot);
        }

        if (trace.isIncomplete()) {
            throw new IOException("Trace is incomplete");
        }

        return trace;
    }

    public TraceTreeSpan getTrace(Faultload faultload) throws IOException {
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

    public void registerFaultload(Faultload faultload) {
        String queryUrl = collectorUrl + "/v1/register_faultload";

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
}
