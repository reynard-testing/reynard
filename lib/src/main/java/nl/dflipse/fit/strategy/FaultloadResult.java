package nl.dflipse.fit.strategy;

import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.TraceAnalysis;

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
