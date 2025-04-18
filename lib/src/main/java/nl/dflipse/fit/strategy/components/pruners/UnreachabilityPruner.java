package nl.dflipse.fit.strategy.components.pruners;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.components.PruneDecision;
import nl.dflipse.fit.strategy.components.Pruner;

public class UnreachabilityPruner implements Pruner {
    private final Logger logger = LoggerFactory.getLogger(UnreachabilityPruner.class);

    @Override
    public PruneDecision prune(Faultload faultload) {
        // Prune on injecting faults on unreachable points
        Set<Behaviour> expected = getExpectedBehaviour(faultload);
        if (expected.isEmpty()) {
            return PruneDecision.KEEP;
        }

        for (Fault toInject : faultload) {
            boolean found = false;
            for (Behaviour point : expected) {
                if (point.uid().matches(toInject.uid())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                logger.debug("Pruning node {} due to unreachable point {}", faultload, toInject);
                return PruneDecision.PRUNE_SUBTREE;
            }
        }

        return PruneDecision.KEEP;
    }

}
