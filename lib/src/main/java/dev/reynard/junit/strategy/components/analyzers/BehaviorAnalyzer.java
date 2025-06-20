package dev.reynard.junit.strategy.components.analyzers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.faultload.modes.FailureMode;
import dev.reynard.junit.instrumentation.trace.tree.TraceReport;
import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.components.FeedbackContext;
import dev.reynard.junit.strategy.components.FeedbackHandler;
import dev.reynard.junit.strategy.components.PruneContext;
import dev.reynard.junit.strategy.components.Reporter;
import dev.reynard.junit.strategy.store.SubsetStore;
import dev.reynard.junit.strategy.util.Sets;
import dev.reynard.junit.strategy.util.Simplify;
import dev.reynard.junit.strategy.util.traversal.TraversalOrder;

public class BehaviorAnalyzer implements FeedbackHandler, Reporter {
    private final Logger logger = LoggerFactory.getLogger(BehaviorAnalyzer.class);

    // TODO: equality checks (wrt masks)
    private final Map<Fault, SubsetStore<Fault>> knownFailures = new LinkedHashMap<>();
    private final Map<FaultUid, SubsetStore<Fault>> knownResolutions = new LinkedHashMap<>();
    private final Map<FaultUid, Integer> maxArity = new LinkedHashMap<>();

    private List<FailureMode> failureModes;

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        failureModes = context.getFailureModes();

        result.trace.traverseReports(TraversalOrder.BREADTH_FIRST, true, report -> {
            if (report.injectedFault != null) {
                // Not interesting, we caused it ourselves
                return;
            }

            handleReport(report, result, context);
        });
    }

    private void handleReport(TraceReport report, FaultloadResult result, FeedbackContext context) {
        var point = report.injectionPoint;

        // Direct causal descendants, i.e., (likely) dependencies for result
        List<TraceReport> children = result.trace.getChildren(report);

        // keep track of max ariy
        maxArity.computeIfAbsent(point, x -> 0);
        maxArity.put(point, Math.max(children.size(), maxArity.get(point)));

        if (children.isEmpty()) {
            // Leaf node in the call graph
            return;
        }

        // Get the injected and observed faults in child responses
        Set<Fault> unexpectedFaults = new HashSet<>();
        Set<Fault> expectedFaults = new HashSet<>();

        for (var childReport : children) {
            if (!childReport.hasFaultBehaviour()) {
                continue;
            }

            Fault fault = childReport.getFault();

            if (childReport.hasIndirectFaultBehaviour()) {
                unexpectedFaults.add(fault);
            } else {
                expectedFaults.add(fault);
            }
        }

        // All nested faulty behaviours
        Set<Fault> faults = Sets.union(unexpectedFaults, expectedFaults);

        // Check how we behaved for these faults
        boolean hasFailure = report.hasIndirectFaultBehaviour();
        boolean hasAlteredResponse = false;
        var happyPathResponse = context.getHappyPath(point);

        if (happyPathResponse != null) {
            hasAlteredResponse = !happyPathResponse.response.equals(report.response);
        }

        boolean hasImpact = hasFailure || hasAlteredResponse;

        if (faults.isEmpty()) {
            if (hasFailure) {
                logger.warn("Detected failure {} with no cause! This can be indicative of a bug!",
                        report.getFault());
            }

            if (hasAlteredResponse) {
                logger.warn(
                        "Detected altered response {} at {} with no cause! This can be indicative of a bug!",
                        report.response, report.injectionPoint);
            }

            return;
        }

        if (hasFailure) {
            Fault pointFault = report.getFault();
            if (knownFailures.containsKey(pointFault)) {
                SubsetStore<Fault> knownCauses = knownFailures.get(pointFault);
                boolean hasKnownCause = knownCauses.hasSubsetOf(faults);

                if (!hasKnownCause) {
                    logger.info("For failure {} discovered new cause: {}", pointFault, faults);
                    knownCauses.add(faults);
                }
            } else {
                logger.info("Detected failure {} for cause: {}", pointFault, faults);
                var newCauses = new SubsetStore<Fault>();
                newCauses.add(faults);
                knownFailures.put(pointFault, newCauses);
            }
        }

        if (!hasFailure && hasAlteredResponse) {
            // TODO report
        }

        if (!hasFailure) {
            if (knownResolutions.containsKey(point)) {
                SubsetStore<Fault> knownCauses = knownResolutions.get(point);
                boolean hasKnownCause = knownCauses.hasSubsetOf(faults);

                if (!hasKnownCause) {
                    logger.info("Point {} can also recover from cause: {}", point, faults);
                    knownCauses.add(faults);
                }
            } else {
                logger.info("Detected resilient point {} for cause: {}", point, faults);
                var newCauses = new SubsetStore<Fault>();
                newCauses.add(faults);
                knownResolutions.put(point, newCauses);
            }
        }
    }

    @Override
    public Object report(PruneContext context) {
        Map<String, Object> report = new LinkedHashMap<>();

        // --- Report the maximum arity (nested calls)
        Map<String, Object> maxArityReport = new LinkedHashMap<>();
        for (var entry : maxArity.entrySet()) {
            var fault = entry.getKey();
            var arity = entry.getValue();
            if (arity > 0) {
                maxArityReport.put(fault.toString(), arity);
            }
        }

        // --- Report the causes of externally visible failures
        List<Object> internalFailuresReport = new ArrayList<>();
        for (var entry : knownFailures.entrySet()) {
            var fault = entry.getKey();
            var store = entry.getValue();

            Map<String, Object> faultReport = new LinkedHashMap<>();
            List<Object> causes = new ArrayList<>();

            var simplified = Simplify.simplify(store.getSets(), failureModes);

            for (Set<Fault> cause : simplified.first()) {
                Map<String, Object> failureReport = new LinkedHashMap<>();
                failureReport.put("causes", cause.stream().map(x -> x.toString()).toList());
                causes.add(failureReport);
            }

            for (Set<FaultUid> cause : simplified.second()) {
                Map<String, Object> failureReport = new LinkedHashMap<>();
                failureReport.put("causes", cause.stream().map(x -> x.toString()).toList());
                causes.add(failureReport);
            }

            faultReport.put("uid", fault.uid().toString());
            faultReport.put("mode", fault.mode().toString());
            faultReport.put("causes", causes);
            internalFailuresReport.add(faultReport);
        }

        // --- Report the combinations of faults that can be internally resolved
        List<Object> knownResilienciesReport = new ArrayList<>();
        for (var entry : knownResolutions.entrySet()) {
            var point = entry.getKey();
            var store = entry.getValue();

            Map<String, Object> resilienceReport = new LinkedHashMap<>();
            List<Object> causes = new ArrayList<>();

            var simplified = Simplify.simplify(store.getSets(), failureModes);

            for (Set<Fault> cause : simplified.first()) {
                Map<String, Object> failureReport = new LinkedHashMap<>();
                failureReport.put("redundancy", cause.stream().map(x -> x.toString()).toList());
                causes.add(failureReport);
            }

            for (Set<FaultUid> cause : simplified.second()) {
                Map<String, Object> failureReport = new LinkedHashMap<>();
                failureReport.put("redundancy", cause.stream().map(x -> x.toString()).toList());
                causes.add(failureReport);
            }

            resilienceReport.put("point", point.toString());
            resilienceReport.put("redundancies", causes);
            knownResilienciesReport.add(resilienceReport);
        }

        report.put("max_arity", maxArityReport);
        report.put("failures", internalFailuresReport);
        report.put("resiliency", knownResilienciesReport);
        return report;
    }

}
