package io.github.delanoflipse.fit.suite.strategy.components.analyzers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.faultload.modes.FailureMode;
import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.StrategyReporter;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.components.PruneContext;
import io.github.delanoflipse.fit.suite.strategy.components.Reporter;
import io.github.delanoflipse.fit.suite.strategy.store.SubsetStore;
import io.github.delanoflipse.fit.suite.strategy.util.Sets;
import io.github.delanoflipse.fit.suite.strategy.util.Simplify;
import io.github.delanoflipse.fit.suite.strategy.util.TraceAnalysis.TraversalStrategy;
import io.github.delanoflipse.fit.suite.trace.tree.TraceResponse;

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

        result.trace.traverseReports(TraversalStrategy.BREADTH_FIRST, true, report -> {
            var injectionPoint = report.injectionPoint;

            if (report.injectedFault != null) {
                // Not interesting, we caused it ourselves
                return;
            }

            // Set<FaultUid> children = result.trace.getDecendants(injectionPoint);
            Set<FaultUid> children = result.trace.getChildren(injectionPoint);

            maxArity.computeIfAbsent(injectionPoint, x -> 0);
            maxArity.put(injectionPoint, Math.max(children.size(), maxArity.get(injectionPoint)));

            if (children.isEmpty()) {
                // We are a leaf node.
                return;
            }

            Set<Fault> unexpectedCauses = new HashSet<>();
            Set<Fault> expectedCauses = new HashSet<>();

            for (var childUid : children) {
                var childReport = result.trace.getReportByFaultUid(childUid);
                if (childReport == null) {
                    continue;
                }

                if (childReport.hasFaultBehaviour()) {
                    Fault fault = childReport.getFault();

                    if (childReport.hasIndirectFaultBehaviour()) {
                        unexpectedCauses.add(fault);
                    } else {
                        expectedCauses.add(fault);
                    }
                }
            }

            Set<Fault> causes = Sets.union(unexpectedCauses, expectedCauses);

            boolean hasFailure = report.hasIndirectFaultBehaviour();
            boolean hasAlteredResponse = false;
            var happyPathResponse = context.getHappyPath(injectionPoint);
            if (happyPathResponse != null) {
                hasAlteredResponse = !happyPathResponse.response.equals(report.response);
            }
            boolean hasImpact = hasFailure || hasAlteredResponse;

            if (causes.isEmpty()) {
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
                    boolean hasKnownCause = knownCauses.hasSubsetOf(causes);

                    if (!hasKnownCause) {
                        logger.info("For failure {} discovered new cause: {}", pointFault, causes);
                        knownCauses.add(causes);
                    }
                } else {
                    logger.info("Detected failure {} for cause: {}", pointFault, causes);
                    var newCauses = new SubsetStore<Fault>();
                    newCauses.add(causes);
                    knownFailures.put(pointFault, newCauses);
                }
            }

            if (!hasFailure && hasAlteredResponse) {
                // TODO report
            }

            if (!hasFailure) {
                FaultUid point = report.injectionPoint;
                if (knownResolutions.containsKey(point)) {
                    SubsetStore<Fault> knownCauses = knownResolutions.get(point);
                    boolean hasKnownCause = knownCauses.hasSubsetOf(causes);

                    if (!hasKnownCause) {
                        logger.info("Point {} can also recover from cause: {}", point, causes);
                        knownCauses.add(causes);
                    }
                } else {
                    logger.info("Detected resilient point {} for cause: {}", point, causes);
                    var newCauses = new SubsetStore<Fault>();
                    newCauses.add(causes);
                    knownResolutions.put(point, newCauses);
                }
            }
        });
    }

    @Override
    public Object report(PruneContext context) {
        Map<String, Object> report = new LinkedHashMap<>();

        Map<String, Object> maxArityReport = new LinkedHashMap<>();
        for (var entry : maxArity.entrySet()) {
            var fault = entry.getKey();
            var arity = entry.getValue();
            maxArityReport.put(fault.toString(), arity);
        }

        List<Object> internalFailuresReport = new ArrayList<>();
        for (var entry : knownFailures.entrySet()) {
            var fault = entry.getKey();
            var store = entry.getValue();

            Map<String, Object> faultReport = new LinkedHashMap<>();
            List<Object> causes = new ArrayList<>();

            var simplified = Simplify.simplify(store.getSets(), failureModes);
            var i = 1;
            for (Set<Fault> cause : simplified.first()) {
                Map<String, Object> failureReport = new LinkedHashMap<>();
                failureReport.put("id", i++);
                failureReport.put("cause", cause.toString());
                causes.add(failureReport);
            }

            for (Set<FaultUid> cause : simplified.second()) {
                Map<String, Object> failureReport = new LinkedHashMap<>();
                failureReport.put("id", i++);
                failureReport.put("cause", cause.toString());
                causes.add(failureReport);
            }

            faultReport.put("fault", fault.toString());
            faultReport.put("causes", causes);
            internalFailuresReport.add(faultReport);
        }

        List<Object> knownResilienciesReport = new ArrayList<>();
        for (var entry : knownResolutions.entrySet()) {
            var point = entry.getKey();
            var store = entry.getValue();

            Map<String, Object> resilienceReport = new LinkedHashMap<>();
            List<Object> causes = new ArrayList<>();

            var simplified = Simplify.simplify(store.getSets(), failureModes);

            var i = 1;
            for (Set<Fault> cause : simplified.first()) {
                Map<String, Object> failureReport = new LinkedHashMap<>();
                failureReport.put("id", i++);
                failureReport.put("redundancy", cause.toString());
                causes.add(failureReport);
            }
            for (Set<FaultUid> cause : simplified.second()) {
                Map<String, Object> failureReport = new LinkedHashMap<>();
                failureReport.put("id", i++);
                failureReport.put("redundancy", cause.toString());
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
