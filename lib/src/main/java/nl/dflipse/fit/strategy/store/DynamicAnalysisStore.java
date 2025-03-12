package nl.dflipse.fit.strategy.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.util.Pair;

public class DynamicAnalysisStore {
    private List<Set<FaultUid>> redundantUidSubsets = new ArrayList<>();
    private List<Set<Pair<FaultUid, FaultMode>>> redundantFaultSubsets = new ArrayList<>();

    public boolean ignoreFaultUidSubset(Set<FaultUid> subset) {
        // If the subset is already in the list of redundant subsets
        // Or if the subset is a subset of an already redundant subset
        // Then we can ignore this subset
        for (var redundant : this.redundantUidSubsets) {
            if (redundant.containsAll(subset)) {
                return false;
            }
        }

        // This is a novel redundant subset, lets add it!
        this.redundantUidSubsets.add(subset);
        return true;
    }

    public boolean ignoreFaultUidSubset(FaultUid... subset) {
        return ignoreFaultUidSubset(Set.of(subset));
    }

    public boolean ignoreFaultUidSubset(List<FaultUid> subset) {
        return ignoreFaultUidSubset(Set.copyOf(subset));
    }

    private Set<Pair<FaultUid, FaultMode>> convertFaultsToPairs(Set<Fault> faults) {
        return faults.stream()
                .map(fault -> new Pair<FaultUid, FaultMode>(fault.getUid(), fault.getMode()))
                .collect(Collectors.toSet());
    }

    public Set<Pair<FaultUid, FaultMode>> ignoreFaultSubset(Set<Fault> subset) {
        var pairs = convertFaultsToPairs(subset);

        // If the subset is already in the list of redundant subsets
        // Or if the subset is a subset of an already redundant subset
        // Then we can ignore this subset
        for (var redundant : this.redundantFaultSubsets) {
            if (redundant.containsAll(pairs)) {
                return redundant;
            }
        }

        this.redundantFaultSubsets.add(pairs);
        return pairs;
    }

    public Set<Pair<FaultUid, FaultMode>> ignoreFaultSubset(Fault... subset) {
        return ignoreFaultSubset(Set.of(subset));
    }

    public Set<Pair<FaultUid, FaultMode>> ignoreFaultSubset(List<Fault> subset) {
        return ignoreFaultSubset(Set.copyOf(subset));
    }

    public List<Set<Pair<FaultUid, FaultMode>>> getRedundantFaultSubsets() {
        return this.redundantFaultSubsets;
    }

}
