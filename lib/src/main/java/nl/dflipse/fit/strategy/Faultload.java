package nl.dflipse.fit.strategy;

import java.util.ArrayList;
import java.util.List;

import nl.dflipse.fit.trace.TraceParent;
import nl.dflipse.fit.trace.TraceState;

public class Faultload {
    private final List<Fault> faultload;

    private TraceParent traceParent = new TraceParent();
    private TraceState traceState = new TraceState();

    public Faultload(List<Fault> faultload) {
        this.faultload = faultload;
        initializeTraceState();
    }

    public Faultload() {
        this.faultload = new ArrayList<>();
        initializeTraceState();
    }

    public List<Fault> getFaultload() {
        return faultload;
    }

    public String readableString() {
        List<String> readableFaults = new ArrayList<>();

        for (Fault fault : faultload) {
            readableFaults.add(fault.spanId + "(" + fault.faultMode.getType() + " " + fault.faultMode.getArgs() + ")");
        }

        return String.join(", ", readableFaults);
    }

    private void initializeTraceState() {
        traceState.set("fit", "1");
    }

    public String serializeJson() {
        return FaultloadSerializer.serializeJson(faultload);
    }

    public String getTraceId() {
        return traceParent.traceId;
    }

    public TraceParent getTraceParent() {
        return traceParent;
    }

    public TraceState getTraceState() {
        return traceState;
    }

    public int size() {
        return faultload.size();
    }
}
