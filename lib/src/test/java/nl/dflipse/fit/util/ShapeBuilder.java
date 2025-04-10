package nl.dflipse.fit.util;

import java.util.HashSet;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.trace.tree.TraceTreeSpan;

public class ShapeBuilder {
    private final String traceId;
    private boolean passed = true;
    private int serviceCounter = 0;
    private Set<Fault> faults = new HashSet<>();

    public ShapeBuilder(String traceId) {
        this.traceId = traceId;
    }

    public String newService() {
        serviceCounter++;
        return "Service " + serviceCounter;
    }

    public TraceTreeSpan leafNode() {
        TraceTreeSpan span = new NodeBuilder(traceId)
                .withService(newService())
                .withReport("Endpoint 1")
                .withResponse(200, "OK")
                .build();

        return span;
    }

    // TraceTreeSpan build() {

    // }
}
