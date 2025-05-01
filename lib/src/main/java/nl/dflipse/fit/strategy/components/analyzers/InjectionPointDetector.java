package io.github.delanoflipse.fit.strategy.components.analyzers;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.faultload.Behaviour;
import io.github.delanoflipse.fit.faultload.Fault;
import io.github.delanoflipse.fit.faultload.FaultUid;
import io.github.delanoflipse.fit.strategy.FaultloadResult;
import io.github.delanoflipse.fit.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.strategy.util.Lists;
import io.github.delanoflipse.fit.strategy.util.Sets;
import io.github.delanoflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;
import io.github.delanoflipse.fit.trace.tree.TraceReport;

public class InjectionPointDetector implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(InjectionPointDetector.class);
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
            // Start exploring all known combinations in the happy path
            context.exploreFrom(List.of());
            return;
        }

        // -- New points in failure paths --
        Set<FaultUid> expectedPoints = context.getExpectedPoints(result.trace.getInjectedFaults());
        Set<Behaviour> expectedBehaviours = context.getExpectedBehaviours(result.trace.getInjectedFaults());

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
            handleAppeared(result, context, entry.getKey(), entry.getValue(), expectedBehaviours);
        }
    }

    private void handleAppeared(FaultloadResult result, FeedbackContext context, TraceReport parent,
            List<TraceReport> newPoints,
            Set<Behaviour> expectedBehaviours) {

        // determine which reports can cause the new point
        List<TraceReport> parentalCauses = result.trace.getChildren(parent);
        List<Behaviour> directCauses = parentalCauses.stream()
                .map(TraceReport::getBehaviour)
                .filter(x -> x.isFault())
                .collect(Collectors.toList());

        // determine which reports are the actual causes
        // (i.e., the ones that are injected)
        // As we will visit starting at the root causes
        Set<Fault> actualCauses = new LinkedHashSet<>();
        for (var fault : result.trace.getDecendants(parent)) {
            if (fault.injectedFault != null) {
                actualCauses.add(fault.injectedFault);
            }
        }

        for (var point : newPoints) {
            boolean isNew = context.reportPreconditionOfFaultUid(directCauses, point.faultUid);
            if (isNew) {
                logger.info("Found conditional point: {} given {}", point.faultUid, directCauses);
            }

            Fault retriedPoint = getIsRetryOf(point.faultUid, expectedBehaviours, context);
            if (retriedPoint != null) {
                Fault persistentFault = handleRetry(retriedPoint, point.faultUid, context);

                // Replace the retried point with the persistent fault
                // in the actual causes
                actualCauses = Sets.replaceIf(actualCauses,
                        x -> x.uid().matches(persistentFault.uid()),
                        persistentFault);
            }
        }

        // Note: it would be more efficient if the expension at this stage would be
        // [new] :: existing
        // Due to how the search tree is structured (leftmost has less subtrees)
        // However, this is currently not possible
        // (impact is just more redundant explore node visits)
        boolean isNew = context.exploreFrom(actualCauses);
        if (isNew) {
            logger.info("Exploring new point in combination with {}", actualCauses);
            return;
        }
    }

    private Fault getIsRetryOf(FaultUid newFid, Set<Behaviour> expectedBehaviours, FeedbackContext context) {
        if (!onlyPersistantOrTransientRetries) {
            return null;
        }

        // edge case: the happy path should only have one count
        // Otherwise, we cannot be sure which one is the retry, especially
        // in the presence of multiple faults.
        boolean happyPathOnlyHasOne = context.getHappyPaths().stream()
                .filter(f -> f.faultUid.matchesUpToCount(newFid))
                .count() == 1;

        if (!happyPathOnlyHasOne) {
            return null;
        }

        // We are a retry of a point that has the same uid,
        // expect for a transient count
        // one less than ours
        Behaviour retriedFault = expectedBehaviours.stream()
                .filter(f -> f.uid().matchesUpToCount(newFid))
                .filter(f -> f.uid().isTransient())
                .filter(f -> f.uid().count() == newFid.count() - 1)
                .findFirst()
                .orElse(null);

        if (retriedFault == null) {
            return null;
        }

        if (retriedFault.uid().isPersistent()) {
            // Dont retry a persistent fault
            return null;
        }

        return retriedFault.getFault();
    }

    // TODO: preferably this would be a seperate component
    // but this is not feasible due the reliance on the exploreFrom
    private Fault handleRetry(Fault retriedFault, FaultUid retryPoint, FeedbackContext context) {

        // only distinguish between transient (once) or always
        Fault persistentFault = new Fault(retriedFault.uid().asAnyCount(), retriedFault.mode());

        logger.info("Detected retry: {} -> {}", retriedFault, retryPoint);
        logger.debug("Turning transient fault into persistent {}", persistentFault);

        // Don't check for the retried fault
        context.pruneFaultUidSubset(Set.of(retryPoint));
        // do not add the transient and the persistent fault togheter...
        context.pruneFaultUidSubset(Set.of(retriedFault.uid(), persistentFault.uid()));
        context.reportFaultUid(persistentFault.uid());
        return persistentFault;
    }
}
