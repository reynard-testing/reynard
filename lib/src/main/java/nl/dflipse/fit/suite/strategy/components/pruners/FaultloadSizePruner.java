package io.github.delanoflipse.fit.suite.strategy.components.pruners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Faultload;
import io.github.delanoflipse.fit.suite.strategy.components.PruneContext;
import io.github.delanoflipse.fit.suite.strategy.components.PruneDecision;
import io.github.delanoflipse.fit.suite.strategy.components.Pruner;

public class FaultloadSizePruner implements Pruner {
    private final Logger logger = LoggerFactory.getLogger(FaultloadSizePruner.class);
    private final int maxSize;

    public FaultloadSizePruner(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public PruneDecision prune(Faultload faultload, PruneContext context) {
        if (faultload.size() > maxSize) {
            logger.info("Pruning faultload of size " + faultload.size() + " to " + maxSize);
            return PruneDecision.PRUNE_SUPERSETS;
        }

        return PruneDecision.KEEP;
    }

}
