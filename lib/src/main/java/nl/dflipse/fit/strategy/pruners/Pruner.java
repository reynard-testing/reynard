package nl.dflipse.fit.strategy.pruners;

import nl.dflipse.fit.faultload.Faultload;

public interface Pruner {
    public PruneDecision prune(Faultload faultload);
}
