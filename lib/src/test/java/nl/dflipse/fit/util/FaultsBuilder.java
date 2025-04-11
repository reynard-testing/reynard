package nl.dflipse.fit.util;

import java.util.List;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.modes.FailureMode;

public class FaultsBuilder {
    private final List<FaultUid> fids;
    private final List<FailureMode> modes;

    public FaultsBuilder(List<FaultUid> fids, List<FailureMode> modes) {
        this.fids = fids;
        this.modes = modes;
    }

    public Fault get(int f, int m) {
        return new Fault(fids.get(f), modes.get(m));
    }
}
