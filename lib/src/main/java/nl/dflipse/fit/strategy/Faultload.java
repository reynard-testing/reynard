package nl.dflipse.fit.strategy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import nl.dflipse.fit.trace.TraceParent;
import nl.dflipse.fit.trace.TraceState;

public class Faultload {
    private final List<String> faultload;

    private TraceParent traceParent = new TraceParent();
    private TraceState traceState = new TraceState();

    public Faultload(List<String> faultload) {
        this.faultload = faultload;
        initializeTraceState();
    }

    public Faultload() {
        this.faultload = new ArrayList<>();
        initializeTraceState();
    }

    private String encodedFaultload() {
        List<String> encodedFaults = new ArrayList<>();
        for (String fault : faultload) {
            String encodedFault = URLEncoder.encode(fault, StandardCharsets.UTF_8);
            encodedFaults.add(encodedFault);
        }
        String combined = String.join(":", encodedFaults);
        return combined;
    }

    public String readableString() {
        return String.join(", ", faultload);
    }

    private void initializeTraceState() {
        traceState.set("fit", "1");

        if (faultload.isEmpty()) {
            return;
        }

        traceState.set("faultload", encodedFaultload());
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

    public List<String> getFaultload() {
        return faultload;
    }

    public int size() {
        return faultload.size();
    }
}
