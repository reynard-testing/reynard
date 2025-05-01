package io.github.delanoflipse.fit.suite.testutil;

import java.util.List;

import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.faultload.modes.FailureMode;

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
