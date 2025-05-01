package io.github.delanoflipse.fit.strategy.components;

public enum PruneDecision {
    PRUNE,
    PRUNE_SUPERSETS,
    KEEP;

    public static PruneDecision max(PruneDecision... decisions) {
        PruneDecision max = PruneDecision.KEEP;
        for (var decision : decisions) {
            if (decision == PruneDecision.PRUNE_SUPERSETS) {
                return PruneDecision.PRUNE_SUPERSETS;
            }

            if (decision == PruneDecision.PRUNE) {
                max = PruneDecision.PRUNE;
            }
        }

        return max;
    }
}
