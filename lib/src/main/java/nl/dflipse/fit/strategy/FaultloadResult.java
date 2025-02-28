package nl.dflipse.fit.strategy;

import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.TraceAnalysis;

public class FaultloadResult {
    public Faultload faultload;
    public TraceAnalysis trace;
    public boolean passed;

    public FaultloadResult(Faultload faultload, TraceAnalysis trace, boolean passed) {
        this.faultload = faultload;
        this.trace = trace;
        this.passed = passed;
    }

    public Set<Fault> getNotInjectedFaults() {
        var intendedFaults = faultload.getFaults();
        var injectedFaults = trace.getFaults();
        var notInjectedFaults = Sets.difference(intendedFaults, injectedFaults);
        return notInjectedFaults;
    }

    public boolean isInitial() {
        return faultload.getFaults().isEmpty();
    }

}
