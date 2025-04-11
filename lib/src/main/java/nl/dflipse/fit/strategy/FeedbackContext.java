package nl.dflipse.fit.strategy;

import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.modes.FailureMode;
import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;

public interface FeedbackContext {
    public Generator getGenerator();

    public List<FailureMode> getFailureModes();

    public List<FaultUid> getFaultUids();

    public DynamicAnalysisStore getStore();

    public void reportFaultUids(List<FaultUid> faultInjectionPoints);

    public void reportConditionalFaultUidByUid(Set<FaultUid> condition, FaultUid fid);

    public void reportConditionalFaultUid(Set<Fault> condition, FaultUid fid);

    public void reportExclusionOfFaultUidByUid(Set<FaultUid> condition, FaultUid fid);

    public void reportExclusionOfFaultUid(Set<Fault> condition, FaultUid fid);

    public void reportSubstitutionByUid(Set<FaultUid> given, Set<FaultUid> replacement);

    public void reportSubstitution(Set<Fault> given, Set<Fault> replacement);

    public void pruneFaultUidSubset(Set<FaultUid> subset);

    public void pruneFaultSubset(Set<Fault> subset);

    public void pruneFaultload(Faultload fautload);
}
