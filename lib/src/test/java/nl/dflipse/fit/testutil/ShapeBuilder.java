package io.github.delanoflipse.fit.suite.testutil;

import java.util.Map;

public class ShapeBuilder {
    private final String traceId;
    private int serviceCounter = 0;

    public ShapeBuilder(String traceId) {
        this.traceId = traceId;
    }

    public String newService() {
        serviceCounter++;
        return "Service " + serviceCounter;
    }

    public EventBuilder leafNode(EventBuilder parent) {
        EventBuilder builder = new EventBuilder(parent)
                .withPoint(newService(), "sig", Map.of(), 0)
                .withResponse(200, "OK");

        return builder;
    }

    // TraceTreeSpan build() {

    // }
}
