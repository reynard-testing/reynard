package nl.dflipse.fit.strategy.store;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.util.MinimalSubsetTrie;
import nl.dflipse.fit.strategy.util.Pair;

public class DynamicAnalysisStore {
    private final Set<FaultMode> modes;
    private MinimalSubsetTrie<FaultUid> redundantUidSubsets = new MinimalSubsetTrie<>();
    private MinimalSubsetTrie<Pair<FaultUid, FaultMode>> redundantFaultSubsets = new MinimalSubsetTrie<>();
    private Set<Faultload> redundantFaultloads = new HashSet<>();

    public DynamicAnalysisStore(Set<FaultMode> modes) {
        this.modes = modes;
    }

    public boolean hasFaultUidSubset(Set<FaultUid> set) {
        return this.redundantUidSubsets.hasSubset(set);
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

    public boolean ignoreFaultUidSubset(FaultUid... set) {
        return ignoreFaultUidSubset(Set.of(set));
    }

    public boolean ignoreFaultUidSubset(List<FaultUid> set) {
        return ignoreFaultUidSubset(Set.copyOf(set));
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

    private boolean hasFaultSubsetFromPairs(Set<Pair<FaultUid, FaultMode>> set) {
        return this.redundantFaultSubsets.hasSubset(set);
    }

    public boolean hasFaultSubset(Set<Fault> set) {
        return hasFaultSubsetFromPairs(faultsToPairs(set));
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

    public Set<Faultload> getRedundantFaultloads() {
        return this.redundantFaultloads;
    }

    public Set<Set<FaultUid>> getRedundantUidSubsets() {
        return this.redundantUidSubsets.getAll();
    }

    public Set<Set<Pair<FaultUid, FaultMode>>> getRedundantFaultSubsets() {
        return this.redundantFaultSubsets.getAll();
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
