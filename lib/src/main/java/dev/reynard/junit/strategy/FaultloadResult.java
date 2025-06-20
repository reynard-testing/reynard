package dev.reynard.junit.strategy;

import java.util.Set;

import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.strategy.util.Sets;
import dev.reynard.junit.strategy.util.TraceAnalysis;

public class FaultloadResult {
    public TrackedFaultload trackedFaultload;
    public TraceAnalysis trace;
    public boolean passed;

    public FaultloadResult(TrackedFaultload faultload, TraceAnalysis trace, boolean passed) {
        this.trackedFaultload = faultload;
        this.trace = trace;
        this.passed = passed;
    }

    public Set<Fault> getNotInjectedFaults() {
        var intendedFaults = trackedFaultload.getFaultload().faultSet();
        var injectedFaults = trace.getInjectedFaults();
        var notInjectedFaults = Sets.difference(intendedFaults, injectedFaults);
        return notInjectedFaults;
    }

    public boolean isInitial() {
        return trackedFaultload.getFaultload().faultSet().isEmpty();
    }

}
