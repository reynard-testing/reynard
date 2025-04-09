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

            // Note: we can estimate the overlap of the subsets, but it is not accurate
            // enough to be useful. It is better to overestimate than to underestimate.
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

            // Note: we can estimate the overlap of the subsets, but it is not accurate
            // enough to be useful. It is better to overestimate than to underestimate.
        }

        return sum;
    }

}
