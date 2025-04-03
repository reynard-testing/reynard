package nl.dflipse.fit.strategy.util;

import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;

public class SpaceEstimate {

    public static long spaceSize(long modes, long points) {
        return (long) (Math.pow(1 + modes, points));
    }

    public static long spaceSize(long modes, long points, long subset) {
        return (long) (Math.pow(modes, subset) * Math.pow(1 + modes, points - subset));
    }

    public static long nonEmptySpaceSize(long modes, long points) {
        return spaceSize(modes, points) - 1;
    }

    public static long nonEmptySpaceSize(long modes, long points, long subset) {
        return spaceSize(modes, points, subset) - 1;
    }

    public static long estimatePointSubsetsImpact(Set<FaultUid> all, List<Set<FaultUid>> subsets, long modeCount) {
        int pointCount = all.size();
        long sum = 0;

        for (int i = 0; i < subsets.size(); i++) {
            var subset = subsets.get(i);
            long contribution = SpaceEstimate.nonEmptySpaceSize(modeCount, pointCount, subset.size());
            sum += contribution;

            // TODO: this overlap estimate is not accurate, can sometimes go below zero?
            // for (int j = i + 1; j < subsets.size(); j++) {
            // var other = subsets.get(j);
            // var overlap = Sets.union(subset, other);

            // long overlapContribution = SpaceEstimate.nonEmptySpaceSize(modeCount,
            // pointCount, overlap.size());
            // sum -= overlapContribution;
            // }
        }

        return sum;
    }

    public static long estimateFaultSubsetsImpact(Set<FaultUid> all, List<Set<Fault>> subsets, long modeCount) {
        int pointCount = all.size();
        long sum = 0;

        for (int i = 0; i < subsets.size(); i++) {
            var subset = subsets.get(i);
            long contribution = SpaceEstimate.spaceSize(modeCount, pointCount - subset.size());
            sum += contribution;

            // TODO: this overlap estimate is not accurate, can sometimes go below zero?
            // Set<FaultUid> uids = subset.stream()
            // .map(Fault::uid)
            // .collect(Collectors.toSet());

            // Set<Set<Fault>> newlyCovered = new HashSet<>();

            // for (var j = 0; j < i; j++) {
            // var other = subsets.get(j);
            // // if both sets contain a fault for the same uid
            // boolean isIncompatible = other.stream()
            // // for each in other that is not in the original subset
            // .filter(x -> !subset.contains(x))
            // // but has the same uid as one in the original subset
            // .anyMatch(x -> uids.contains(x.uid()));

            // if (isIncompatible) {
            // continue;
            // }

            // var overlap = Sets.union(subset, other);
            // if (newlyCovered.contains(overlap)) {
            // continue;
            // }

            // int pointsLeft = pointCount - overlap.size();
            // if (pointsLeft < 0) {
            // // Famous last words: this should never happen
            // continue;
            // }

            // long overlapContribution = SpaceEstimate.spaceSize(modeCount, pointsLeft);
            // sum -= overlapContribution;
            // newlyCovered.add(overlap);
            // }
        }

        return sum;
    }

}
