package nl.dflipse.fit.strategy;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.trace.data.TraceData;

public class FaultloadResult {
    public Faultload faultload;
    public TraceData traceResult;
    public boolean passed;
    
    public FaultloadResult(Faultload faultload, TraceData traceResult, boolean passed) {
        this.faultload = faultload;
        this.traceResult = traceResult;
        this.passed = passed;
    }
}
