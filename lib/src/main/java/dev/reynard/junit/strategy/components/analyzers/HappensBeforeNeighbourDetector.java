package dev.reynard.junit.strategy.components.analyzers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.reynard.junit.faultload.Behaviour;
import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.instrumentation.trace.tree.TraceReport;
import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.components.FeedbackContext;
import dev.reynard.junit.strategy.components.FeedbackHandler;
import dev.reynard.junit.strategy.util.traversal.TraversalOrder;

/**
 * Pruner that prunes faults that are redundant due to cause-effect
 * relationships
 * I.e. if a fault is injected, and another fault disappears,
 * s set of fault causes the disappearance of the fault (the effect)
 */
public class HappensBeforeNeighbourDetector implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(HappensBeforeNeighbourDetector.class);

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            return;
        }

        Set<Fault> injectedErrorFaults = result.trace.getInjectedFaults();
        Set<FaultUid> expectedPoints = context.getExpectedPoints(injectedErrorFaults);

        result.trace.traverseReports(TraversalOrder.BREADTH_FIRST, true, report -> {
            List<TraceReport> childrenReports = result.trace.getChildren(report);

            Set<FaultUid> expectedChildren = expectedPoints.stream()
                    .filter(f -> f.hasParent() && f.getParent().matches(report.injectionPoint))
                    .collect(Collectors.toSet());

            Set<FaultUid> observedChildren = childrenReports.stream()
                    .map(f -> f.injectionPoint)
                    .filter(f -> f != null)
                    .collect(Collectors.toSet());

            Set<FaultUid> dissappeared = missingPoints(expectedChildren, observedChildren);

            if (dissappeared.isEmpty()) {
                return;
            }

            Set<Behaviour> causes = result.trace.getChildren(report).stream()
                    // TODO: check is this is correct
                    .filter(f -> f.hasFaultBehaviour())
                    .map(r -> r.getBehaviour())
                    .collect(Collectors.toSet());

            // TODO: remove cause, if another cause as an inclusion condition
            // X includes Y
            // X does not exclude Z
            // X,Y excludes Z
            // -> Y excludes Z

            for (FaultUid fault : dissappeared) {
                handleHappensBefore(causes, fault, context);
            }
        });
    }

    private Set<FaultUid> missingPoints(Set<FaultUid> expected, Set<FaultUid> observed) {
        Set<FaultUid> missing = new HashSet<>();

        for (FaultUid point : expected) {
            boolean found = false;

            for (FaultUid seen : observed) {
                if (seen.matches(point)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                missing.add(point);
            }
        }

        return missing;
    }

    private void handleHappensBefore(Set<Behaviour> cause, FaultUid effect, FeedbackContext context) {
        if (cause.isEmpty()) {
            logger.info("Found unknown cause that hides {}", effect);
            return;
        }
        logger.info("Found exclusion: {} hides {}", cause, effect);
        context.reportExclusionOfFaultUid(cause, effect);
    }

}
