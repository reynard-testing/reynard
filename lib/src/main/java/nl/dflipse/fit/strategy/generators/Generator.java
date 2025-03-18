package nl.dflipse.fit.strategy.generators;

import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;

public interface Generator {
    public void reportFaultUids(List<FaultUid> faultInjectionPoints);

    public long pruneFaultUidSubset(Set<FaultUid> subset);

    public long pruneFaultSubset(Set<Fault> subset);

    public long pruneMixedSubset(Set<Fault> subset, Set<FaultUid> subset2);

    public long pruneFaultload(Faultload faultload);

    public Set<FaultMode> getFaultModes();

    public List<Faultload> generate();

    public long spaceSize();
}
