package io.github.delanoflipse.fit.strategy.components;

import io.github.delanoflipse.fit.faultload.Faultload;

public interface Pruner {
    public PruneDecision prune(Faultload faultload, PruneContext context);
}
