package nl.dflipse.fit.strategy.generators;

import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;

public interface Generator {
    public void reportFaultUids(List<FaultUid> faultInjectionPoints);

    public long ignoreFaultUidSubset(Set<FaultUid> subset);

    public long ignoreFaultSubset(Set<Fault> subset);

    public long ignoreFaultload(Faultload faultload);

    public List<Faultload> generate();

    public long spaceSize();
}
