package io.github.delanoflipse.fit.suite.strategy.components.analyzers;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.util.Lists;
import io.github.delanoflipse.fit.suite.trace.tree.TraceReport;

public class ConditionalPointDetector implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(ConditionalPointDetector.class);
    private final boolean onlyPersistantOrTransientRetries;

    public ConditionalPointDetector(boolean onlyPersistantOrTransientRetries) {
        this.onlyPersistantOrTransientRetries = onlyPersistantOrTransientRetries;
    }

    public ConditionalPointDetector() {
        this(false);
    }

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        Set<FaultUid> pointsInTrace = result.trace.getFaultUids();

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

    protected void handleAppeared(FaultloadResult result, FeedbackContext context, TraceReport parent,
            List<TraceReport> newPoints,
            Set<Behaviour> expectedBehaviours) {

        Set<Fault> injected = result.trace.getInjectedFaults();

        // determine which reports can cause the new point
        List<TraceReport> parentalCauses = result.trace.getChildren(parent);
        List<Behaviour> directCauses = parentalCauses.stream()
                .map(TraceReport::getBehaviour)
                .filter(x -> x.isFault())
                .collect(Collectors.toList());

        for (var point : newPoints) {
            boolean isNew = context.reportPreconditionOfFaultUid(directCauses, point.faultUid);
            if (isNew) {
                logger.info("Found conditional point: {} given {}", point.faultUid, directCauses);
            }

            if (!onlyPersistantOrTransientRetries) {
                continue;
            }

            Fault retriedPoint = getIsRetryOf(point.faultUid, expectedBehaviours, context);

            if (retriedPoint == null) {
                continue;
            }

            Fault persistentFault = handleRetry(retriedPoint, point.faultUid, context);

            // Replace the retried point with the persistent fault
            // in the actual causes
            Set<Fault> startingNode = new HashSet<>(injected);
            startingNode.removeIf(x -> x.uid().matches(persistentFault.uid()));
            startingNode.add(persistentFault);

            // only inject reachable points
            Set<FaultUid> reachable = context.getExpectedPoints(startingNode);
            startingNode.removeIf(x -> !FaultUid.contains(reachable, x.uid()));
            context.exploreFrom(startingNode);
        }
    }

    private Fault getIsRetryOf(FaultUid newFid, Set<Behaviour> expectedBehaviours, FeedbackContext context) {
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
