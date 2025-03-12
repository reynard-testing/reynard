package nl.dflipse.fit.strategy.pruners;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;

public interface Pruner {
    public boolean prune(Faultload faultload, DynamicAnalysisStore history);
}
