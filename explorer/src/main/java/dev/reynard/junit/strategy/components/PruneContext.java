package dev.reynard.junit.strategy.components;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.reynard.junit.faultload.Behaviour;
import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.faultload.modes.FailureMode;
import dev.reynard.junit.instrumentation.trace.tree.TraceReport;
import dev.reynard.junit.strategy.util.Pair;

public abstract class PruneContext {
    public abstract List<Pair<Set<Fault>, List<Behaviour>>> getHistoricResults();

    public abstract List<FailureMode> getFailureModes();

    public abstract List<FaultUid> getFaultInjectionPoints();

    public abstract Set<Behaviour> getExpectedBehaviours(Set<Fault> faultload);

    public abstract Set<FaultUid> getExpectedPoints(Set<Fault> faultload);

    public abstract Map<FaultUid, TraceReport> getHappyPath();

    public TraceReport getHappyPath(FaultUid uid) {
        return getHappyPath().get(uid);
    }

    public List<TraceReport> getHappyPaths() {
        return getHappyPath().values().stream().toList();
    }

    public abstract long spaceSize();
}
