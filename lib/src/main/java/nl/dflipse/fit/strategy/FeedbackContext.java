package nl.dflipse.fit.strategy;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.modes.FailureMode;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;

public interface FeedbackContext {
    public List<FailureMode> getFailureModes();

    public List<FaultUid> getFaultUids();

    public DynamicAnalysisStore getStore();

    public void reportFaultUid(FaultUid faultInjectionPoint);

    public void reportUpstreamEffect(FaultUid cause, Collection<FaultUid> effect);

    public void reportConditionalFaultUidByUid(Collection<FaultUid> condition, FaultUid fid);

    public void reportConditionalFaultUid(Collection<Behaviour> condition, FaultUid fid);

    public void reportExclusionOfFaultUidByUid(Collection<FaultUid> condition, FaultUid fid);

    public void reportExclusionOfFaultUid(Collection<Behaviour> condition, FaultUid fid);

    public void reportDownstreamEffect(Collection<Behaviour> condition, Behaviour effect);

    public void pruneFaultUidSubset(Set<FaultUid> subset);

    public void pruneFaultSubset(Set<Fault> subset);

    public void pruneFaultload(Faultload fautload);
}
