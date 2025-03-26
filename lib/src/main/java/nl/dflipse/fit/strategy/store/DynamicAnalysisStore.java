package nl.dflipse.fit.strategy.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.SpaceEstimate;

public class DynamicAnalysisStore {
    private final Logger logger = LoggerFactory.getLogger(DynamicAnalysisStore.class);

    private final Set<FaultMode> modes;
    private final Set<FaultUid> points = new HashSet<>();

    private final Map<FaultUid, Set<Set<Fault>>> preconditions = new HashMap<>();

    private final List<Faultload> redundantFaultloads = new ArrayList<>();
    private final List<Set<FaultUid>> redundantUidSubsets = new ArrayList<>();
    private final List<Set<Fault>> redundantFaultSubsets = new ArrayList<>();

    public DynamicAnalysisStore(Set<FaultMode> modes) {
        this.modes = modes;
    }

    public Set<FaultUid> getFaultInjectionPoints() {
        return points;
    }

    public List<Faultload> getRedundantFaultloads() {
        return this.redundantFaultloads;
    }

    public List<Set<FaultUid>> getRedundantUidSubsets() {
        return this.redundantUidSubsets;
    }

    public List<Set<Fault>> getRedundantFaultSubsets() {
        return this.redundantFaultSubsets;
    }

    public boolean hasFaultUid(FaultUid fid) {
        return points.contains(fid);
    }

    public boolean addFaultUid(FaultUid fid) {
        if (hasFaultUid(fid)) {
            return false;
        }

        points.add(fid);
        return true;
    }

    public int addFaultUids(List<FaultUid> fids) {
        int added = 0;

        for (var fid : fids) {
            boolean isNew = addFaultUid(fid);
            if (isNew) {
                added++;
            }
        }

        return added;
    }

    public boolean hasConditionForFaultUid(Set<Fault> condition, FaultUid fid) {
        if (!preconditions.containsKey(fid)) {
            return false;
        }

        for (var subset : preconditions.get(fid)) {
            if (Sets.isSubsetOf(subset, condition)) {
                return true;
            }
        }

        return false;
    }

    public long estimateReductionForConditionalFaultUid(Set<Fault> condition, FaultUid fid) {
        if (hasConditionForFaultUid(condition, fid)) {
            return 0;
        }

        int existingPreconditions = preconditions.getOrDefault(fid, Set.of()).size();
        return SpaceEstimate.spaceSize(modes.size(), condition.size()) - (existingPreconditions + 1);
    }

    public boolean addConditionalFaultUid(Set<Fault> condition, FaultUid fid) {
        boolean isNew = addFaultUid(fid);

        if (hasConditionForFaultUid(condition, fid)) {
            return false;
        }

        if (isNew) {
            logger.info("Found new precondition {} for NOVEL fault {}", condition, fid);
        } else {
            logger.info("Found new precondition {} for existing fault {}", condition, fid);
        }

        preconditions.computeIfAbsent(fid, x -> new HashSet<>()).add(condition);
        return true;
    }

    public boolean hasFaultUidSubset(Set<FaultUid> set) {
        for (var redundant : this.redundantUidSubsets) {
            if (Sets.isSubsetOf(redundant, set)) {
                return true;
            }
        }

        return false;
    }

    public long estimateReductionForFaultUidSubset(Set<FaultUid> subset) {
        if (hasFaultUidSubset(subset)) {
            return 0;
        }

        // TODO: account for overlapping subsets?
        return SpaceEstimate.nonEmptySpaceSize(modes.size(), getFaultInjectionPoints().size() - subset.size());
    }

    public boolean pruneFaultUidSubset(Set<FaultUid> subset) {
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

    public boolean hasFaultSubset(Set<Fault> subset) {
        for (var redundant : this.redundantFaultSubsets) {
            if (Sets.isSubsetOf(redundant, subset)) {
                return true;
            }
        }

        return false;
    }

    public long estimateReductionForFaultSubset(Set<Fault> subset) {
        if (hasFaultSubset(subset)) {
            return 0;
        }

        // TODO: account for overlapping subsets?
        return SpaceEstimate.nonEmptySpaceSize(modes.size(), getFaultInjectionPoints().size() - subset.size());
    }

    public boolean pruneFaultSubset(Set<Fault> subset) {
        // If the subset is already in the list of redundant subsets
        // Or if the subset is a subset of an already redundant subset
        // Then we can ignore this subset
        if (hasFaultSubset(subset)) {
            return false;
        }

        // TODO: if we have all fault modes for a faultuid in the subsets
        // we can ignore the faultuid

        this.redundantFaultSubsets.add(subset);
        return true;
    }

    public long estimateReductionForFaultload(Faultload faultload) {
        if (hasFaultload(faultload)) {
            return 0;
        }

        return 1;
    }

    public boolean pruneFaultload(Faultload faultload) {
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
