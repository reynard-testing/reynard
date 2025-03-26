package nl.dflipse.fit.strategy.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    public Set<FaultUid> getFaultUids() {
        return points;
    }

    public Map<FaultUid, Set<Set<Fault>>> getPreconditions() {
        return preconditions;
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

        preconditions.computeIfAbsent(fid, x -> new HashSet<>())
                .removeIf(s -> Sets.isSubsetOf(condition, s));
        preconditions.get(fid).add(condition);
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

    public boolean pruneFaultUidSubset(Set<FaultUid> subset) {
        // If the subset is already in the list of redundant subsets
        // Or if the subset is a subset of an already redundant subset
        // Then we can ignore this subset
        if (hasFaultUidSubset(subset)) {
            return false;
        }

        // filter out all supersets of this subset
        this.redundantUidSubsets.removeIf(s -> Sets.isSubsetOf(subset, s));
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

    private boolean isValidSubset(Set<Fault> subset) {
        // Must have at most one fault per faultuid
        Set<FaultUid> uids = new HashSet<>();
        for (var fault : subset) {
            if (!uids.add(fault.uid())) {
                return false;
            }
        }
        return true;
    }

    public boolean pruneFaultSubset(Set<Fault> subset) {
        // If the subset is already in the list of redundant subsets
        // Or if the subset is a subset of an already redundant subset
        // Then we can ignore this subset

        if (!isValidSubset(subset)) {
            throw new IllegalArgumentException("Fault subset must have at most one fault per faultuid");
        }

        if (hasFaultSubset(subset)) {
            return false;
        }

        // TODO: if we have all fault modes for a faultuid in the subsets
        // we can ignore the faultuid

        // filter out all supersets of this subset
        this.redundantFaultSubsets.removeIf(s -> Sets.isSubsetOf(subset, s));
        // and add this subset
        this.redundantFaultSubsets.add(subset);
        return true;
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

    public long estimatePruned(Set<FaultUid> allUids) {
        int points = allUids.size();
        int modes = this.modes.size();
        long sum = 0;

        // Note: this does not account for overlap between uid and fault subsets
        for (int i = 0; i < redundantUidSubsets.size(); i++) {
            var subset = redundantUidSubsets.get(i);
            long contribution = SpaceEstimate.nonEmptySpaceSize(modes, points, subset.size());
            sum += contribution;

            Set<Set<FaultUid>> coveredOverlap = new HashSet<>();

            for (int j = i + 1; j < redundantUidSubsets.size(); j++) {
                var other = redundantUidSubsets.get(j);
                var overlap = Sets.intersection(subset, other);
                if (coveredOverlap.contains(overlap)) {
                    continue;
                }

                if (!overlap.isEmpty()) {
                    long overlapContribution = SpaceEstimate.nonEmptySpaceSize(modes, points,
                            other.size() + subset.size() - overlap.size());
                    sum -= overlapContribution;
                    coveredOverlap.add(overlap);
                }
            }
        }

        for (int i = 0; i < redundantFaultSubsets.size(); i++) {
            var subset = redundantFaultSubsets.get(i);
            long contribution = SpaceEstimate.spaceSize(modes, points - subset.size());
            sum += contribution;

            // TODO: this overlap estimate is not accurate, can sometimes go below zero?
            Set<FaultUid> uids = subset.stream()
                    .map(Fault::uid)
                    .collect(Collectors.toSet());

            Set<Set<Fault>> coveredExtensions = new HashSet<>();

            for (int j = i + 1; j < redundantFaultSubsets.size(); j++) {
                var other = redundantFaultSubsets.get(j);
                var overlap = Sets.intersection(subset, other);
                var leftInOther = Sets.difference(other, overlap);

                if (overlap.isEmpty()) {
                    continue;
                }

                // If the subset and the other contain faults for the same point
                // but a different mode, then they are incompatible
                var isIncompatible = leftInOther.stream()
                        .anyMatch(f -> uids.contains(f.uid()));

                if (isIncompatible || coveredExtensions.contains(leftInOther)) {
                    continue;
                }

                int unionSize = other.size() + subset.size() - overlap.size();
                int pointsLeft = points - unionSize;
                if (pointsLeft < 0) {
                    // Famous last words: this should never happen
                    continue;
                }
                long overlapContribution = SpaceEstimate.spaceSize(modes, pointsLeft);
                // sum -= overlapContribution;
                coveredExtensions.add(leftInOther);
            }
        }

        sum += redundantFaultloads.size();

        return sum;
    }

    public long estimatePruned() {
        return estimatePruned(getFaultInjectionPoints());
    }

}
