package nl.dflipse.fit.strategy.analyzers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.Reporter;
import nl.dflipse.fit.strategy.StrategyReporter;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;
import nl.dflipse.fit.trace.tree.TraceSpanReport;
import nl.dflipse.fit.trace.tree.TraceSpanResponse;

public class BehaviorAnalyzer implements FeedbackHandler, Reporter {
    private final Logger logger = LoggerFactory.getLogger(BehaviorAnalyzer.class);

    private final Map<Fault, List<Set<Fault>>> knownFailures = new LinkedHashMap<>();
    private final Map<FaultUid, List<Set<Fault>>> knownResolutions = new LinkedHashMap<>();
    private final Map<FaultUid, TraceSpanResponse> happyPath = new LinkedHashMap<>();
    private final Map<FaultUid, Integer> maxArity = new LinkedHashMap<>();

    private Set<Fault> hasKnownCause(List<Set<Fault>> known, Set<Fault> faults) {
        for (var knownSet : known) {
            if (Sets.isSubsetOf(faults, knownSet)) {
                return knownSet;
            }
        }
        return null;
    }

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        result.trace.traverseFaults(TraversalStrategy.BREADTH_FIRST, true, injectionPoint -> {
            TraceSpanReport report = result.trace.getReportByFaultUid(injectionPoint);
            if (report == null) {
                return;
            }

            if (report.injectedFault != null) {
                // Not interesting, we caused it ourselves
                return;
            }

            Set<FaultUid> children = result.trace.getDecendants(injectionPoint);
            // Set<FaultUid> children = result.trace.getChildren(injectionPoint);
            boolean isHappyPath = !report.response.isErrenous();

            maxArity.computeIfAbsent(injectionPoint, x -> 0);
            maxArity.put(injectionPoint, Math.max(children.size(), maxArity.get(injectionPoint)));

            if (children.isEmpty()) {
                if (isHappyPath && !happyPath.containsKey(injectionPoint)) {
                    happyPath.put(injectionPoint, report.response);
                }

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

                if (childReport.hasError()) {
                    Fault fault = childReport.getFault();

                    if (childReport.hasIndirectError()) {
                        unexpectedCauses.add(fault);
                    } else {
                        expectedCauses.add(fault);
                    }
                }
            }

            Set<Fault> causes = Sets.union(unexpectedCauses, expectedCauses);
            isHappyPath = isHappyPath && expectedCauses.isEmpty();

            if (isHappyPath && !happyPath.containsKey(injectionPoint)) {
                happyPath.put(injectionPoint, report.response);
            }

            boolean hasFailure = report.hasIndirectError();
            boolean hasAlteredResponse = false;
            var happyPathResponse = happyPath.get(injectionPoint);
            if (happyPathResponse != null) {
                hasAlteredResponse = !happyPathResponse.equals(report.response);
            }
            boolean hasImpact = hasFailure || hasAlteredResponse;

            if (causes.isEmpty()) {
                if (hasFailure) {
                    logger.warn("Detected failure {} with no cause! This likely due to a bug in the scenario setup!",
                            report.getFault());
                }

                if (hasAlteredResponse) {
                    logger.warn(
                            "Detected altered response {} with no cause! This likely due to a bug in the scenario setup!",
                            report.getFault());
                }

                return;
            }

            if (hasFailure) {
                Fault pointFault = report.getFault();
                if (knownFailures.containsKey(pointFault)) {
                    List<Set<Fault>> knownCauses = knownFailures.get(pointFault);
                    Set<Fault> hasKnownCause = hasKnownCause(knownCauses, causes);
                    if (hasKnownCause == null) {
                        logger.warn("For failure {} discovered new cause: {}", pointFault, causes);
                        knownCauses.add(causes);
                    }
                } else {
                    logger.warn("Detected failure {} for cause: {}", pointFault, causes);
                    List<Set<Fault>> causesSet = new ArrayList<>();
                    causesSet.add(causes);
                    knownFailures.put(pointFault, causesSet);
                }
            }

            if (!hasFailure && hasAlteredResponse) {
                // TODO report
            }

            if (!hasFailure) {
                FaultUid point = report.faultUid;
                if (knownResolutions.containsKey(point)) {
                    List<Set<Fault>> knownCauses = knownResolutions.get(point);
                    Set<Fault> hasKnownCause = hasKnownCause(knownCauses, causes);
                    if (hasKnownCause == null) {
                        logger.warn("Point {} can also recover from cause: {}", point, causes);
                        knownCauses.add(causes);
                    }
                } else {
                    logger.warn("Detected resilient point {} for cause: {}", point, causes);
                    List<Set<Fault>> causesSet = new ArrayList<>();
                    causesSet.add(causes);
                    knownResolutions.put(point, causesSet);
                }
            }
        });
    }

    @Override
    public Map<String, String> report() {
        StrategyReporter.printNewline();
        StrategyReporter.printHeader("Behavioral Anlaysis", 48, "=");

        StrategyReporter.printNewline();
        StrategyReporter.printHeader("Happy Path", 48, "-");
        for (var entry : happyPath.entrySet()) {
            var fault = entry.getKey();
            var response = entry.getValue();
            StrategyReporter.printNewline();
            StrategyReporter.printKeyValue("Point", fault.toString());
            StrategyReporter.printKeyValue("Status", String.valueOf(response.status));
            String bodyLimited = response.body;
            if (response.body.length() > 100) {
                bodyLimited = response.body.substring(0, 97) + "...";
            }
            StrategyReporter.printKeyValue("Response", bodyLimited);
        }

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
            var causes = entry.getValue();

            StrategyReporter.printNewline();
            StrategyReporter.printKeyValue("Failure", fault.toString());

            var i = 0;
            for (var cause : causes) {
                StrategyReporter.printKeyValue("Cause (" + i + ")", cause.toString());
                i++;
            }
        }

        StrategyReporter.printNewline();
        StrategyReporter.printHeader("(Internal) Resiliency", 48, "-");
        for (var entry : knownResolutions.entrySet()) {
            var fault = entry.getKey();
            var causes = entry.getValue();
            StrategyReporter.printKeyValue("Point", fault.toString());
            var i = 0;
            for (var cause : causes) {
                StrategyReporter.printKeyValue("Redundancy (" + i + ")", cause.toString());
                i++;
            }
        }
        // Don't report in the normal sense
        return null;
    }

}
