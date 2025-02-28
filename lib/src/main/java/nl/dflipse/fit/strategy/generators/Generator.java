package nl.dflipse.fit.strategy.generators;

import java.util.List;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;

public interface Generator {
    public void mockFaultUids(List<FaultUid> faultUids);

    public List<Faultload> generate();
}
