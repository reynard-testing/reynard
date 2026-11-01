package dev.reynard.junit.strategy.components;

import dev.reynard.junit.faultload.Faultload;

public interface Pruner {
    public PruneDecision prune(Faultload faultload, PruneContext context);
}
