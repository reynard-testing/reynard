package nl.dflipse.fit.strategy.components;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.modes.FailureMode;

public abstract class FeedbackContext {
    public abstract List<FailureMode> getFailureModes();

    public abstract List<FaultUid> getFaultInjectionPoints();

    public abstract void reportFaultUid(FaultUid faultInjectionPoint);

    public void reportFaultUids(List<FaultUid> faultInjectionPoints) {
        for (var f : faultInjectionPoints) {
            reportFaultUid(f);
        }
    }

    public abstract void reportUpstreamEffect(FaultUid cause, Collection<FaultUid> effect);

    public abstract void reportPreconditionOfFaultUid(Collection<Behaviour> condition, FaultUid result);

    public abstract void reportExclusionOfFaultUid(Collection<Behaviour> condition, FaultUid fid);

    public abstract void reportDownstreamEffect(Collection<Behaviour> condition, Behaviour effect);

    public abstract void exploreFrom(Collection<Fault> startingNode);

    public abstract void pruneFaultUidSubset(Set<FaultUid> subset);

    public abstract void pruneFaultSubset(Set<Fault> subset);

    public abstract void pruneFaultload(Faultload faultload);

    public abstract Set<Behaviour> getExpectedBehaviours(Set<Fault> faultload);

    public abstract Set<FaultUid> getExpectedPoints(Set<Fault> faultload);

    public abstract long spaceSize();
}
