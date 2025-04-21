package nl.dflipse.fit.strategy.components.analyzers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.components.FeedbackContext;
import nl.dflipse.fit.strategy.components.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Lists;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;
import nl.dflipse.fit.trace.tree.TraceReport;

public class InjectionPointDetector implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(InjectionPointDetector.class);
    // TODO: move to store
    // TODO: allow for happy path of conditionals
    // e.g., if fault has no upstream faults, its the happy path
    private final List<FaultUid> pointsInHappyPath = new ArrayList<>();
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
        List<FaultUid> pointsInTrace = result.trace.getFaultUids(traversalStrategy);

        for (var point : pointsInTrace) {
            context.reportFaultUid(point);
        }

        // --- Happy path ---
        if (result.isInitial()) {
            // TODO: allow for happy path of conditionals
            pointsInHappyPath.addAll(pointsInTrace);

            // Start exploring beyond the happy path
            context.exploreFrom(List.of());
            return;
        }

        // -- New points in failure paths --
        Set<FaultUid> expectedPoints = context.getExpectedPoints(result.trace.getInjectedFaults());

        // Analyse new paths that were not expected
        // but whose parent is expected
        // (this excludes nested points)
        List<FaultUid> appearedPoints = Lists.difference(pointsInTrace, List.copyOf(expectedPoints));
        List<TraceReport> appearedReports = result.trace.getReports(appearedPoints);
        var rootAppeared = appearedReports.stream()
                .filter(f -> expectedPoints.contains(result.trace.getParent(f.faultUid)))
                .toList();

        // group by their parent, as there might be multiple appeared points
        // for the same cause
        var rootByParent = rootAppeared.stream()
                .collect(Collectors.groupingBy(result.trace::getParent));

        // Report newly found points
        for (var entry : rootByParent.entrySet()) {
            handleAppeared(result, context, entry.getKey(), entry.getValue(), expectedPoints);
        }
    }

    private void handleAppeared(FaultloadResult result, FeedbackContext context, TraceReport parent,
            List<TraceReport> newPoints,
            Set<FaultUid> expectedPoints) {

        // determine which reports can cause the new point
        List<TraceReport> parentalCauses = result.trace.getChildren(parent);
        List<Behaviour> directCauses = parentalCauses.stream()
                .map(TraceReport::getBehaviour)
                .filter(x -> x.isFault())
                .collect(Collectors.toList());

        // determine which reports are the actual causes
        // (i.e., the ones that are injected)
        // As we will visit starting at the root causes
        List<Fault> actualCauses = result.trace.getDecendants(parent).stream()
                .filter(x -> x.injectedFault != null)
                .map(x -> x.injectedFault)
                .toList();

        for (var point : newPoints) {
            context.reportPreconditionOfFaultUid(directCauses, point.faultUid);
            logger.info("Found conditional point: {} given {}", point.faultUid, directCauses);
        }

        logger.info("Exploring new point in combination with {}", actualCauses);
        context.exploreFrom(actualCauses);
    }

    // TODO: fix and seperate
    private boolean handleRetry(FaultloadResult result, Set<FaultUid> expectedPoints, FaultUid newFid,
            Collection<Behaviour> condition,
            List<Fault> actualCauses, FeedbackContext context) {
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

        // We are a retry of a point that has the same uid,
        // expect for a transient count
        // one less than ours
        FaultUid retriedFault = expectedPoints.stream()
                .filter(f -> f.matchesUpToCount(newFid))
                .filter(f -> f.isTransient())
                .filter(f -> f.count() == newFid.count() - 1)
                .findFirst()
                .orElse(null);

        if (retriedFault == null) {
            return false;
        }

        // only distinguish between transient (once) or always
        FaultUid persistentFault = newFid.asAnyCount();

        logger.info("Detected retry: {} -> {}", retriedFault, newFid);
        logger.debug("Turning transient fault into persistent {}", persistentFault);
        // do not add the transient and the persistent fault togheter...
        context.pruneFaultUidSubset(Set.of(retriedFault, persistentFault));

        // ensure we replace the transient fault with the persistent one
        Set<Fault> relatedCondition = condition.stream()
                .filter(f -> f.isFault())
                .filter(f -> !f.uid().matchesUpToCount(persistentFault))
                .map(f -> f.getFault())
                .collect(Collectors.toSet());

        // TODO: determine which faults are still reachable when the persistent
        // fault is injected
        // As we might have actual causes that are not reachable anymore
        Set<FaultUid> reachable = Set.of();

        Set<Fault> includeActual = actualCauses.stream()
                .filter(f -> !f.uid().matchesUpToCount(persistentFault))
                .filter(f -> !reachable.contains(f.uid()))
                .collect(Collectors.toSet());

        Set<Fault> exploreFrom = Sets.union(relatedCondition, includeActual);

        if (exploreFrom.isEmpty()) {
            // explore starting the persistent fault
            context.reportFaultUid(persistentFault);
        } else {
            // explore starting the persistent fault
            // given the condition
            // note: we do not care for the causes of the fault, as they might be
            // nested (if we block all, we also block all children of the fault)
            context.reportFaultUid(persistentFault);
            context.exploreFrom(exploreFrom);
        }

        return true;
    }
}
