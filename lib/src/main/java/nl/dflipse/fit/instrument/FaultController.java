package nl.dflipse.fit.instrument;

import java.io.IOException;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.util.TraceAnalysis;

public interface FaultController {
    public TraceAnalysis getTrace(Faultload faultload, FaultUid mask) throws IOException;

    public TraceAnalysis getTrace(Faultload faultload) throws IOException;

    public void registerFaultload(Faultload faultload);

    public void unregisterFaultload(Faultload faultload);
}
