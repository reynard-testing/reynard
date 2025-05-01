package io.github.delanoflipse.fit.strategy.components.analyzers;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.faultload.Fault;
import io.github.delanoflipse.fit.faultload.FaultUid;
import io.github.delanoflipse.fit.faultload.modes.FailureMode;
import io.github.delanoflipse.fit.strategy.FaultloadResult;
import io.github.delanoflipse.fit.strategy.StrategyReporter;
import io.github.delanoflipse.fit.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.strategy.components.PruneContext;
import io.github.delanoflipse.fit.strategy.components.Reporter;
import io.github.delanoflipse.fit.strategy.store.SubsetStore;
import io.github.delanoflipse.fit.strategy.util.Sets;
import io.github.delanoflipse.fit.strategy.util.Simplify;
import io.github.delanoflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;
import io.github.delanoflipse.fit.trace.tree.TraceResponse;

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
            var injectionPoint = report.faultUid;

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
                            report.response, report.faultUid);
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
                FaultUid point = report.faultUid;
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
    public Map<String, String> report(PruneContext context) {
        StrategyReporter.printNewline();
        StrategyReporter.printHeader("Behavioral Anlaysis", 48, "=");

        StrategyReporter.printNewline();
        Map<String, String> maxArityReport = new LinkedHashMap<>();
        for (var entry : maxArity.entrySet()) {
            var fault = entry.getKey();
            var arity = entry.getValue();
            maxArityReport.put(fault.toString(), String.valueOf(arity));
        }
        StrategyReporter.printReport("Max arity", maxArityReport);

        StrategyReporter.printNewline();
        StrategyReporter.printHeader("(Internal) Failures", 48, "-");
        for (var entry : knownFailures.entrySet()) {
            var fault = entry.getKey();
            var store = entry.getValue();

            StrategyReporter.printNewline();
            StrategyReporter.printKeyValue("Failure", fault.toString());

            var simplified = Simplify.simplify(store.getSets(), failureModes);

            var i = 0;
            for (Set<Fault> cause : simplified.first()) {
                StrategyReporter.printKeyValue("Cause (" + i + ")", cause.toString());
                i++;
            }
            for (Set<FaultUid> cause : simplified.second()) {
                StrategyReporter.printKeyValue("Cause (" + i + ")", cause.toString() + " (any failure mode)");
                i++;
            }
        }

        StrategyReporter.printNewline();
        StrategyReporter.printHeader("(Internal) Resiliency", 48, "-");
        for (var entry : knownResolutions.entrySet()) {
            var point = entry.getKey();
            var store = entry.getValue();

            StrategyReporter.printNewline();
            StrategyReporter.printKeyValue("Point", point.toString());

            var simplified = Simplify.simplify(store.getSets(), failureModes);

            var i = 0;
            for (Set<Fault> cause : simplified.first()) {
                StrategyReporter.printKeyValue("Redundancy (" + i + ")", cause.toString());
                i++;
            }
            for (Set<FaultUid> cause : simplified.second()) {
                StrategyReporter.printKeyValue("Redundancy (" + i + ")", cause.toString() + " (any failure mode)");
                i++;
            }
        }
        // Don't report in the normal sense
        return null;
    }

}
