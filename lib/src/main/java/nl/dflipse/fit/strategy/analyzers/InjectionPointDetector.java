package nl.dflipse.fit.strategy.analyzers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Lists;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;
import nl.dflipse.fit.trace.tree.TraceReport;

public class InjectionPointDetector implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(InjectionPointDetector.class);
    private final List<FaultUid> pointsInHappyPath = new ArrayList<>();
    private final Set<FaultUid> knownPoints = new HashSet<>();
    // Note: this is unsound in general, but can be useful in practice
    private final boolean onlyPersistantOrTransientRetries;
    private final TraversalStrategy traversalStrategy;

    public InjectionPointDetector(TraversalStrategy traversalStrategy, boolean onlyPersistantOrTransientRetries) {
        this.traversalStrategy = traversalStrategy;
        this.onlyPersistantOrTransientRetries = onlyPersistantOrTransientRetries;
    }

    public InjectionPointDetector(boolean onlyPersistantOrTransientRetries) {
        this(TraversalStrategy.BREADTH_FIRST, onlyPersistantOrTransientRetries);
    }

    public InjectionPointDetector() {
        this(false);
    }

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        List<FaultUid> traceFaultUids = result.trace.getFaultUids(traversalStrategy);

        // --- Report upstream causes and effects ---
        result.trace.traverseReports(TraversalStrategy.BREADTH_FIRST, true, report -> {
            FaultUid cause = report.faultUid;
            if (knownPoints.contains(cause)) {
                return;
            }

            // Find all the effects of this point
            Set<FaultUid> effects = result.trace.getChildren(cause);
            if (effects.isEmpty()) {
                return;
            }

            logger.info("{} is the parent of:", cause);
            for (var effect : effects) {
                logger.info("\t--> {}", effect);
            }
            knownPoints.add(cause);
            context.reportUpstreamEffect(cause, effects);
        });

        // --- Happy path ---
        if (result.isInitial()) {
            pointsInHappyPath.addAll(traceFaultUids);

            for (var point : traceFaultUids) {
                logger.info("Found point: " + point);
                context.reportFaultUid(point);
            }

            return;
        }

        Set<FaultUid> expectedPoints = context.getStore().getExpectedPoints(result.trace.getInjectedFaults());

        // Analyse new paths that were not in the happy path
        var appeared = Lists.difference(traceFaultUids, List.copyOf(expectedPoints));
        List<TraceReport> appearedReports = result.trace.getReports(appeared);
        var nestedAppeared = appearedReports.stream()
                .filter(f -> !expectedPoints.contains(result.trace.getParent(f.faultUid)))
                .toList();
        var rootAppeared = appearedReports.stream()
                .filter(f -> expectedPoints.contains(result.trace.getParent(f.faultUid)))
                .toList();

        for (var nested : nestedAppeared) {
            // TODO: conditionals are currently expected to from the same parent
            // So how do we ensure we require the causes?
            // Because currenlty, this will get pruned directly after creation
            // Maybe the final call should
            logger.info("Found point: {}", nested.faultUid);
            context.reportFaultUid(nested.faultUid);
        }

        // Report newly found points
        for (var newPoint : rootAppeared) {
            TraceReport parent = result.trace.getParent(newPoint);
            if (!expectedPoints.contains(parent.faultUid)) {
                continue;
            }

            List<TraceReport> parentalCauses = result.trace.getChildren(parent);
            List<Behaviour> causes = parentalCauses.stream()
                    .map(TraceReport::getBehaviour)
                    .filter(x -> x.isFault())
                    .collect(Collectors.toList());
            boolean wasRetry = handleRetry(expectedPoints, newPoint.faultUid, causes, context);

            if (!wasRetry) {
                logger.info("Found conditional point: {} given {}", newPoint.faultUid, causes);
                context.reportConditionalFaultUid(causes, newPoint.faultUid);
            }
        }
    }

    private boolean handleRetry(Set<FaultUid> expectedPoints, FaultUid newFid,
            Collection<Behaviour> condition, FeedbackContext context) {
        if (!onlyPersistantOrTransientRetries) {
            return false;
        }

        // edge case: the happy path should only have one count
        // Otherwise, we cannot be sure which one is the retry, especially
        // in the presence of multiple faults.
        boolean happyPathOnlyHasOne = pointsInHappyPath.stream()
                .filter(f -> f.matchesUpToCount(newFid))
                .count() == 1;

        if (!happyPathOnlyHasOne) {
            return false;
        }

        // We are a retry of a fault that has the same uid, expect for a transient count
        // less than ours
        Set<FaultUid> retriedFaults = expectedPoints.stream()
                .filter(f -> f.matchesUpToCount(newFid))
                .filter(f -> f.isTransient())
                .filter(f -> f.count() == newFid.count() - 1)
                .collect(Collectors.toSet());

        if (retriedFaults.size() != 1) {
            return false;
        }

        FaultUid retriedFault = Sets.getOnlyElement(retriedFaults);
        // only distinguish between transient (once) or always
        FaultUid persistentFault = newFid.asAnyCount();

        logger.info("Detected retry: {} -> {}", retriedFault, newFid);
        logger.debug("Turning transient fault into persistent {}", persistentFault);
        // do not add the transient and the persistent fault
        context.pruneFaultUidSubset(Set.of(retriedFault, persistentFault));

        // TODO: handle case were retry is caused by effect of cause
        // Which the persistent fault will block from happening.
        // We need to persistently block, STARTING at retry x?

        // ensure we replace the transient fault with the persistent one
        Set<Behaviour> relatedCondition = condition.stream()
                .filter(f -> !f.uid().matchesUpToCount(persistentFault))
                .collect(Collectors.toSet());
        context.reportConditionalFaultUid(relatedCondition, persistentFault);

        return true;
    }
}
