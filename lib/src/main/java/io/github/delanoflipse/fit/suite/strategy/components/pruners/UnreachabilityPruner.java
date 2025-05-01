package io.github.delanoflipse.fit.suite.strategy.components.pruners;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.faultload.Faultload;
import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.components.PruneContext;
import io.github.delanoflipse.fit.suite.strategy.components.PruneDecision;
import io.github.delanoflipse.fit.suite.strategy.components.Pruner;

public class UnreachabilityPruner implements Pruner, FeedbackHandler {
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

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        List<FaultUid> known = context.getFaultInjectionPoints();
        Set<Fault> injected = result.trace.getInjectedFaults();
        List<FaultUid> injectedPoints = injected.stream()
                .map(Fault::uid)
                .toList();
        Set<FaultUid> observed = result.trace.getFaultUids();

        List<FaultUid> unreachable = known.stream()
                .filter(p -> !FaultUid.contains(observed, p))
                .filter(p -> !FaultUid.contains(injectedPoints, p))
                .toList();
        if (unreachable.isEmpty()) {
            return;
        }

        for (FaultUid point : unreachable) {
            logger.debug("Do not expand to {} from {}", point, injectedPoints);
            context.pruneExploration(injected, point);
        }
    }

}
