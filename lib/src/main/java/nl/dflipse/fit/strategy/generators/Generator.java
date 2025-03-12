package nl.dflipse.fit.strategy.generators;

import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;

public interface Generator {
    public void reportFaultUids(List<FaultUid> faultInjectionPoints);

    public void ignoreFaultUidSubset(Set<FaultUid> subset);

    public void ignoreFaultSubset(Set<Fault> subset);

    public List<Faultload> generate();
}
