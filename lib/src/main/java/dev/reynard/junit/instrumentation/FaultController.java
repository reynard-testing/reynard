package dev.reynard.junit.instrumentation;

import java.io.IOException;
import java.util.concurrent.Callable;

import dev.reynard.junit.strategy.TrackedFaultload;
import dev.reynard.junit.strategy.util.TraceAnalysis;

public interface FaultController {
    public TraceAnalysis getTrace(TrackedFaultload faultload) throws IOException;

    public void registerFaultload(TrackedFaultload faultload) throws IOException;

    public void unregisterFaultload(TrackedFaultload faultload) throws IOException;

    public void withFaultload(TrackedFaultload faultload, Callable<Void> runnable) throws Exception;
}
