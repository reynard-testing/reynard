package nl.dflipse.fit.strategy.components.analyzers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.components.FeedbackContext;
import nl.dflipse.fit.strategy.components.FeedbackHandler;
import nl.dflipse.fit.strategy.components.PruneDecision;
import nl.dflipse.fit.strategy.components.Pruner;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;
import nl.dflipse.fit.trace.tree.TraceReport;

/**
 * Pruner that prunes faults that are redundant due to cause-effect
 * relationships
 * I.e. if a fault is injected, and another fault disappears,
 * s set of fault causes the disappearance of the fault (the effect)
 */
public class HappensBeforeNeighbourDetector implements Pruner, FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(HappensBeforeNeighbourDetector.class);

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            return;
        }

        Set<Fault> injectedErrorFaults = result.trace.getInjectedFaults();
        Set<FaultUid> expectedPoints = context.getExpectedPoints(injectedErrorFaults);

        result.trace.traverseReports(TraversalStrategy.BREADTH_FIRST, true, report -> {
            List<TraceReport> childrenReports = result.trace.getChildren(report);

            Set<FaultUid> expectedChildren = expectedPoints.stream()
                    .filter(f -> f.hasParent() && f.getParent().matches(report.faultUid))
                    .collect(Collectors.toSet());

            Set<FaultUid> observedChildren = childrenReports.stream()
                    .map(f -> f.faultUid)
                    .filter(f -> f != null)
                    .collect(Collectors.toSet());

            Set<FaultUid> dissappeared = missingPoints(expectedChildren, observedChildren);

            if (dissappeared.isEmpty()) {
                return;
            }

            Set<Behaviour> causes = result.trace.getChildren(report).stream()
                    .filter(f -> f.hasFaultBehaviour())
                    .map(r -> r.getBehaviour())
                    .collect(Collectors.toSet());

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

    @Override
    public PruneDecision prune(Faultload faultload) {
        // TODO: prune on the faultload
        return PruneDecision.KEEP;
    }

}
