package nl.dflipse.fit.strategy;

import nl.dflipse.fit.collector.TraceData;

public interface FIStrategy {
    public Faultload next();

    public void handleResult(Faultload faultload, TraceData trace, boolean passed);
}
