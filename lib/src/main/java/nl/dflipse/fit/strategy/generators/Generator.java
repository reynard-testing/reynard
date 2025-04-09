package nl.dflipse.fit.strategy.generators;

import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FailureMode;

public interface Generator {
    public List<Faultload> generate();

    public void reportFaultUids(List<FaultUid> faultInjectionPoints);

    public void reportPreconditionOfFaultUid(Set<Fault> condition, FaultUid fid);

    public void reportExclusionOfFaultUid(Set<Fault> condition, FaultUid fid);

    public void pruneFaultUidSubset(Set<FaultUid> subset);

    public void pruneFaultSubset(Set<Fault> subset);

    public void pruneFaultload(Faultload faultload);

    public Set<FailureMode> getFaultModes();

    public Set<FaultUid> getFaultInjectionPoints();

    public Set<FaultUid> getExpectedPoints(Faultload faultload);

    public Map<FaultUid, Set<Set<Fault>>> getConditionalFaultInjectionPoints();

    public Map<FaultUid, Set<Set<Fault>>> getExclusionsForFaultInjectionPoints();

    public long spaceSize();
}
