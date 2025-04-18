package nl.dflipse.fit.strategy.components;

import nl.dflipse.fit.faultload.Faultload;

public interface Pruner {
    public PruneDecision prune(Faultload faultload);
}
