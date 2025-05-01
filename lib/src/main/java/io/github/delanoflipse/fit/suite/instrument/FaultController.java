package io.github.delanoflipse.fit.suite.instrument;

import java.io.IOException;

import io.github.delanoflipse.fit.suite.strategy.TrackedFaultload;
import io.github.delanoflipse.fit.suite.strategy.util.TraceAnalysis;

public interface FaultController {
    public TraceAnalysis getTrace(TrackedFaultload faultload) throws IOException;

    public void registerFaultload(TrackedFaultload faultload) throws IOException;

    public void unregisterFaultload(TrackedFaultload faultload) throws IOException;
}
