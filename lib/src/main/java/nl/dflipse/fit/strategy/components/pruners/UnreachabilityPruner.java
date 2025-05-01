package io.github.delanoflipse.fit.strategy.components.pruners;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.faultload.Fault;
import io.github.delanoflipse.fit.faultload.FaultUid;
import io.github.delanoflipse.fit.faultload.Faultload;
import io.github.delanoflipse.fit.strategy.components.PruneContext;
import io.github.delanoflipse.fit.strategy.components.PruneDecision;
import io.github.delanoflipse.fit.strategy.components.Pruner;

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
