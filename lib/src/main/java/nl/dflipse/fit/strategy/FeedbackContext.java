package nl.dflipse.fit.strategy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FailureMode;
import nl.dflipse.fit.strategy.generators.Generator;

public interface FeedbackContext {
    public Generator getGenerator();

    public Set<FailureMode> getFaultModes();

    public Set<FaultUid> getFaultUids();

    public Map<FaultUid, Set<Set<Fault>>> getConditionals();

    public Map<FaultUid, Set<Set<Fault>>> getExclusions();

    public Set<Set<Fault>> getConditions(FaultUid fault);

    public Set<FaultUid> getConditionalForFaultload();

    public Set<FaultUid> getExclusionsForFaultload();

    public void reportFaultUids(List<FaultUid> faultInjectionPoints);

    public void reportConditionalFaultUid(Set<Fault> condition, FaultUid fid);

    public void reportExclusionOfFaultUid(Set<Fault> condition, FaultUid fid);

    public void pruneFaultUidSubset(Set<FaultUid> subset);

    public void pruneFaultSubset(Set<Fault> subset);

    public void pruneFaultload(Faultload fautload);
}
