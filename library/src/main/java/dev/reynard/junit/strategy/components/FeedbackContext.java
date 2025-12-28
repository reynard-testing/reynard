package dev.reynard.junit.strategy.components;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import dev.reynard.junit.faultload.Behaviour;
import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.faultload.Faultload;
import dev.reynard.junit.instrumentation.trace.tree.TraceReport;
import dev.reynard.junit.strategy.util.Sets;

public abstract class FeedbackContext extends PruneContext {

    public abstract void reportFaultUid(FaultUid faultInjectionPoint);

    public void reportFaultUids(List<FaultUid> faultInjectionPoints) {
        for (var f : faultInjectionPoints) {
            reportFaultUid(f);
        }
    }

    public abstract boolean reportUpstreamEffect(FaultUid cause, Collection<FaultUid> effect);

    public abstract boolean reportPreconditionOfFaultUid(Collection<Behaviour> condition, FaultUid result);

    public abstract boolean reportExclusionOfFaultUid(Collection<Behaviour> condition, FaultUid fid);

    public abstract boolean reportDownstreamEffect(Collection<Behaviour> condition, Behaviour effect);

    public abstract void reportHappyPath(TraceReport report);

    public abstract boolean exploreFrom(Collection<Fault> startingNode);

    public abstract void pruneFaultUidSubset(Set<FaultUid> subset);

    public abstract void pruneFaultSubset(Set<Fault> subset);

    public abstract void pruneFaultload(Faultload faultload);

    public void pruneExploration(Set<Fault> faultload, Set<FaultUid> explorations) {
        for (var ext : Fault.allFaults(explorations, getFailureModes())) {
            pruneFaultSubset(Sets.union(faultload, ext));
        }
    }

    public void pruneExploration(Set<Fault> faultload, FaultUid exploration) {
        for (var ext : Fault.allFaults(exploration, getFailureModes())) {
            pruneFaultSubset(Sets.plus(faultload, ext));
        }
    }

    public abstract long spaceSize();
}
