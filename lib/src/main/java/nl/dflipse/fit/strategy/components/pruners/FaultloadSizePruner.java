package nl.dflipse.fit.strategy.components.pruners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.components.PruneContext;
import nl.dflipse.fit.strategy.components.PruneDecision;
import nl.dflipse.fit.strategy.components.Pruner;

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
            return PruneDecision.PRUNE_SUBTREE;
        }

        return PruneDecision.KEEP;
    }

}
