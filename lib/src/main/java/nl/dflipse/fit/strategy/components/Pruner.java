package io.github.delanoflipse.fit.suite.strategy.components;

import io.github.delanoflipse.fit.suite.faultload.Faultload;

public interface Pruner {
    public PruneDecision prune(Faultload faultload, PruneContext context);
}
