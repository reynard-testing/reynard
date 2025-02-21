package nl.dflipse.fit.strategy;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.trace.data.TraceData;

public interface FIStrategy {
    public Faultload next();

    public void handleResult(Faultload faultload, TraceData trace, boolean passed);
}
