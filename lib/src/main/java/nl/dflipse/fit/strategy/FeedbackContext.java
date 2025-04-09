package nl.dflipse.fit.strategy;

import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FailureMode;
import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;

public interface FeedbackContext {
    public Generator getGenerator();

    public List<FailureMode> getFaultModes();

    public List<FaultUid> getFaultUids();

    public DynamicAnalysisStore getStore();

    public void reportFaultUids(List<FaultUid> faultInjectionPoints);

    public void reportConditionalFaultUid(Set<Fault> condition, FaultUid fid);

    public void reportExclusionOfFaultUid(Set<Fault> condition, FaultUid fid);

    public void pruneFaultUidSubset(Set<FaultUid> subset);

    public void pruneFaultSubset(Set<Fault> subset);

    public void pruneFaultload(Faultload fautload);
}
