package dev.reynard.junit.util;

import java.util.List;

import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.faultload.modes.FailureMode;

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
