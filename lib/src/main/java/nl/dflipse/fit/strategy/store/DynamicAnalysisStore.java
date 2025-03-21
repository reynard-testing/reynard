package nl.dflipse.fit.strategy.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.util.Pair;

public class DynamicAnalysisStore {
    private final Set<FaultMode> modes;
    private final Set<FaultUid> points = new HashSet<>();
    private final List<Set<FaultUid>> redundantUidSubsets = new ArrayList<>();
    private final List<Set<Pair<FaultUid, FaultMode>>> redundantFaultSubsets = new ArrayList<>();
    private final List<Faultload> redundantFaultloads = new ArrayList<>();
    private final Map<FaultUid, Set<Set<Fault>>> preconditions = new HashMap<>();

    public DynamicAnalysisStore(Set<FaultMode> modes) {
        this.modes = modes;
    }

    public Set<FaultUid> getFaultInjectionPoints() {
        return points;
    }

    public boolean addFaultUid(FaultUid fid) {
        if (!points.contains(fid)) {
            points.add(fid);
            return true;
        }

        return false;
    }

    public void addFaultUids(List<FaultUid> fids) {
        for (var fid : fids) {
            addFaultUid(fid);
        }
    }

    public boolean addConditionalFaultUid(Set<Fault> condition, FaultUid fid) {
        addFaultUid(fid);

        return true;
    }

    public boolean hasFaultUidSubset(Set<FaultUid> subset) {
        for (var redundant : this.redundantUidSubsets) {
            if (redundant.containsAll(subset)) {
                return true;
            }
        }

        return false;
    }

    public boolean ignoreFaultUidSubset(Set<FaultUid> subset) {
        // If the subset is already in the list of redundant subsets
        // Or if the subset is a subset of an already redundant subset
        // Then we can ignore this subset
        if (hasFaultUidSubset(subset)) {
            return false;
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

    public static Set<Pair<FaultUid, FaultMode>> faultsToPairs(Set<Fault> faults) {
        return faults.stream()
                .map(fault -> new Pair<FaultUid, FaultMode>(fault.uid(), fault.mode()))
                .collect(Collectors.toSet());
    }

    public static Set<Fault> pairsToFaults(Set<Pair<FaultUid, FaultMode>> pair) {
        return pair.stream()
                .map(fault -> new Fault(fault.first(), fault.second()))
                .collect(Collectors.toSet());
    }

    private boolean hasFaultSubsetFromPairs(Set<Pair<FaultUid, FaultMode>> subset) {
        for (var redundant : this.redundantFaultSubsets) {
            if (redundant.containsAll(subset)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasFaultSubset(Set<Fault> subset) {
        return hasFaultSubsetFromPairs(faultsToPairs(subset));
    }

    public Set<Pair<FaultUid, FaultMode>> ignoreFaultSubset(Set<Fault> subset) {
        var pairs = faultsToPairs(subset);

        // If the subset is already in the list of redundant subsets
        // Or if the subset is a subset of an already redundant subset
        // Then we can ignore this subset
        if (hasFaultSubsetFromPairs(pairs)) {
            return null;
        }

        // TODO: if we have all fault modes for a faultuid in the subsets
        // we can ignore the faultuid

        this.redundantFaultSubsets.add(pairs);
        return pairs;
    }

    public Set<Pair<FaultUid, FaultMode>> ignoreFaultSubset(Fault... subset) {
        return ignoreFaultSubset(Set.of(subset));
    }

    public Set<Pair<FaultUid, FaultMode>> ignoreFaultSubset(List<Fault> subset) {
        return ignoreFaultSubset(Set.copyOf(subset));
    }

    public List<Faultload> getRedundantFaultloads() {
        return this.redundantFaultloads;
    }

    public List<Set<FaultUid>> getRedundantUidSubsets() {
        return this.redundantUidSubsets;
    }

    public List<Set<Pair<FaultUid, FaultMode>>> getRedundantFaultSubsets() {
        return this.redundantFaultSubsets;
    }

    public boolean ignoreFaultload(Faultload faultload) {
        // If the faultload is already in the list of redundant faultloads
        // Then we can ignore this faultload
        if (hasFaultload(faultload)) {
            return false;
        }

        this.redundantFaultloads.add(faultload);
        return true;
    }

    public boolean hasFaultload(Faultload faultload) {
        return this.redundantFaultloads.contains(faultload);
    }

}
