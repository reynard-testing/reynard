package nl.dflipse.fit.instrument;

import java.io.IOException;

import nl.dflipse.fit.strategy.TrackedFaultload;
import nl.dflipse.fit.strategy.util.TraceAnalysis;

public interface FaultController {
    public TraceAnalysis getTrace(TrackedFaultload faultload) throws IOException;

    public void registerFaultload(TrackedFaultload faultload);

    public void unregisterFaultload(TrackedFaultload faultload);
}
