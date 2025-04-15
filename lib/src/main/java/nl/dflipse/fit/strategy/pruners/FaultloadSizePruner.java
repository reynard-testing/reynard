package nl.dflipse.fit.strategy.pruners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Faultload;

public class FaultloadSizePruner implements Pruner {
    private final Logger logger = LoggerFactory.getLogger(FaultloadSizePruner.class);
    private final int maxSize;

    public FaultloadSizePruner(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public PruneDecision prune(Faultload faultload) {
        if (faultload.size() > maxSize) {
            logger.info("Pruning faultload of size " + faultload.size() + " to " + maxSize);
            return PruneDecision.PRUNE_SUBTREE;
        }

        return PruneDecision.KEEP;
    }

}
