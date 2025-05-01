package io.github.delanoflipse.fit.strategy.components;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.github.delanoflipse.fit.faultload.Behaviour;
import io.github.delanoflipse.fit.faultload.Fault;
import io.github.delanoflipse.fit.faultload.FaultUid;
import io.github.delanoflipse.fit.faultload.Faultload;
import io.github.delanoflipse.fit.trace.tree.TraceReport;

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

    public abstract long spaceSize();
}
