package dev.reynard.junit.strategy.components.analyzers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.reynard.junit.faultload.Behaviour;
import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.faultload.modes.FailureMode;
import dev.reynard.junit.instrumentation.trace.tree.TraceReport;
import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.components.FeedbackContext;
import dev.reynard.junit.strategy.components.FeedbackHandler;
import dev.reynard.junit.strategy.components.PruneContext;
import dev.reynard.junit.strategy.components.Reporter;
import dev.reynard.junit.strategy.util.Lists;

public class ConditionalPointDetector implements FeedbackHandler, Reporter {
    private final Logger logger = LoggerFactory.getLogger(ConditionalPointDetector.class);
    private final boolean onlyPersistantOrTransientRetries;

    private final Map<Fault, Set<FaultUid>> knownRetries = new LinkedHashMap<>();

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
        Set<Behaviour> expectedBehaviours = context.getExpectedBehaviours(result.trace.getInjectedFaults());
        Set<FaultUid> expectedPoints = Behaviour.getFaultUids(expectedBehaviours);

        // Analyse new paths that were not expected
        // but whose parent is expected
        // (this excludes nested points)
        List<FaultUid> appearedPoints = Lists.difference(pointsInTrace, List.copyOf(expectedPoints));
        List<TraceReport> appearedReports = result.trace.getReports(appearedPoints);
        var rootAppeared = appearedReports.stream()
                .filter(f -> expectedPoints.contains(result.trace.getParent(f.injectionPoint)))
                .toList();

        // group by their parent, as there might be multiple appeared points
        // for the same cause
        var rootByParent = rootAppeared.stream()
                .collect(Collectors.groupingBy(result.trace::getParent));

        // Report newly found points
        for (var entry : rootByParent.entrySet()) {
            handleAppeared(result, context, entry.getKey(), entry.getValue());
        }
    }

    private List<Behaviour> getCauses(FaultUid point, List<Behaviour> potentialCauses, Set<Fault> injected) {
        Fault persistentFaultAtPoint = injected.stream()
                .filter(x -> x.isPersistent())
                .filter(x -> x.uid().matches(point))
                .findFirst()
                .orElse(null);

        // Edge case: We injected a persistent fault (which means it must be a retry)
        // Then its caused by its predecessor.
        if (persistentFaultAtPoint != null && point.count() > 0) {
            FaultUid predecessor = point.withCount(point.count() - 1);
            return List.of(new Behaviour(predecessor, persistentFaultAtPoint.mode()));
        }

        List<Behaviour> causes = potentialCauses.stream()
                .filter(x -> {
                    // cause must be before event
                    var isStrictlyBefore = x.uid().isBefore(point);
                    if (isStrictlyBefore.isPresent()) {
                        return isStrictlyBefore.get();
                    }

                    return true;
                })
                .collect(Collectors.toList());
        return causes;
    }

    protected void handleAppeared(FaultloadResult result, FeedbackContext context, TraceReport parent,
            List<TraceReport> newPoints) {

        Set<Fault> injected = result.trace.getInjectedFaults();
        List<FaultUid> newPointsUids = newPoints.stream()
                .map(x -> x.injectionPoint)
                .collect(Collectors.toList());

        // determine which reports can cause the new point
        // which are only those directly related to the parent
        List<TraceReport> parentalCauses = result.trace.getChildren(parent);
        List<Behaviour> relatededBehaviours = parentalCauses.stream()
                .map(TraceReport::getBehaviour)
                .filter(x -> x.isFault())
                .collect(Collectors.toList());
        List<Behaviour> potentialCauses = relatededBehaviours.stream()
                .filter(x -> !FaultUid.contains(newPointsUids, x.uid()))
                .collect(Collectors.toList());

        for (TraceReport point : newPoints) {
            List<Behaviour> causes = getCauses(point.injectionPoint, potentialCauses, injected);
            boolean isNew = context.reportPreconditionOfFaultUid(causes, point.injectionPoint);

            if (isNew) {
                logger.info("Found conditional point: {} given {}", point.injectionPoint, causes);
            }

            if (!onlyPersistantOrTransientRetries) {
                continue;
            }

            Fault retriedPoint = getIsRetryOf(point.injectionPoint, relatededBehaviours, context);

            if (retriedPoint == null) {
                continue;
            }

            Fault persistentFault = handleRetry(retriedPoint, point.injectionPoint, context);

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

    private Fault getIsRetryOf(FaultUid newFid, List<Behaviour> observedBehaviours, FeedbackContext context) {
        // edge case: the happy path should only have one count
        // Otherwise, we cannot be sure which one is the retry, especially
        // in the presence of multiple faults.
        boolean happyPathOnlyHasOne = context.getHappyPaths().stream()
                .filter(f -> f.injectionPoint.matchesUpToCount(newFid))
                .count() == 1;

        if (!happyPathOnlyHasOne) {
            return null;
        }

        // We are a retry of a point that has the same uid,
        // expect for a transient count
        // one less than ours
        Behaviour retriedFault = observedBehaviours.stream()
                .filter(f -> f.uid().matchesUpToCount(newFid))
                .filter(f -> f.uid().isTransient())
                .filter(f -> f.uid().count() == 0)
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
        knownRetries.computeIfAbsent(retriedFault, x -> new LinkedHashSet<>())
                .add(retryPoint);

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

    @Override
    public Object report(PruneContext context) {
        List<Map<String, Object>> report = new ArrayList<>();

        Map<FaultUid, Set<Fault>> byUid = new LinkedHashMap<>();

        for (var entry : knownRetries.entrySet()) {
            Fault f = entry.getKey();
            FaultUid uid = f.uid();
            byUid.computeIfAbsent(uid, x -> new LinkedHashSet<>())
                    .add(f);
        }

        for (var entry : byUid.entrySet()) {
            Map<String, Object> retryReport = new LinkedHashMap<>();
            Map<String, Object> modes = new LinkedHashMap<>();

            retryReport.put("uid", entry.getKey().toString());

            for (var f : entry.getValue()) {
                FailureMode mode = f.mode();
                modes.put(mode.toString(), knownRetries.get(f).size());
            }

            retryReport.put("modes", modes);
            report.add(retryReport);
        }

        return report;
    }
}
