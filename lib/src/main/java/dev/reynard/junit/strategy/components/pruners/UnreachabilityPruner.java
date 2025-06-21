package dev.reynard.junit.strategy.components.pruners;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.faultload.Faultload;
import dev.reynard.junit.strategy.components.PruneContext;
import dev.reynard.junit.strategy.components.PruneDecision;
import dev.reynard.junit.strategy.components.Pruner;

public class UnreachabilityPruner implements Pruner {
    private final Logger logger = LoggerFactory.getLogger(UnreachabilityPruner.class);

    @Override
    public PruneDecision prune(Faultload faultload, PruneContext context) {
        // Prune on injecting faults on unreachable points
        Set<FaultUid> expected = context.getExpectedPoints(faultload.faultSet());

        if (expected.isEmpty()) {
            return PruneDecision.KEEP;
        }

        for (Fault toInject : faultload.faultSet()) {
            boolean found = false;
            for (FaultUid point : expected) {
                if (point.matches(toInject.uid())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                logger.debug("Pruning node {} due to unreachable point {}", faultload, toInject);
                return PruneDecision.PRUNE_SUPERSETS;
            }
        }

        return PruneDecision.KEEP;
    }

}
