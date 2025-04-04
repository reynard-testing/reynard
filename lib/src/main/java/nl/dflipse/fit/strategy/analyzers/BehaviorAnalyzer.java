package nl.dflipse.fit.strategy.analyzers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

public class BehaviorAnalyzer implements FeedbackHandler, Reporter {
    private final Logger logger = LoggerFactory.getLogger(BehaviorAnalyzer.class);

    private final Map<Fault, List<Set<Fault>>> knownFailures = new HashMap<>();
    private final Map<FaultUid, List<Set<Fault>>> knownResolutions = new HashMap<>();

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
        boolean wasFailureOutcome = result.trace.getRootReport().response.isErrenous();

        result.trace.traverseFaults(injectionPoint -> {
            var report = result.trace.getReportByFaultUid(injectionPoint);
            if (report == null) {
                return;
            }

            if (report.injectedFault != null) {
                // Not interesting, we caused it ourselves
                return;
            }

            boolean hasFailure = report.hasIndirectError();

            var children = result.trace.getChildren(injectionPoint);

            if (children.isEmpty()) {
                // We are a leaf node.
                return;
            }

            Set<Fault> unexpectedCauses = new HashSet<>();
            Set<Fault> expectedCauses = new HashSet<>();

            for (var child : children) {
                var childReport = result.trace.getReportByFaultUid(child);
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

            if (causes.isEmpty()) {
                if (hasFailure) {
                    logger.warn("Detected failure {} with no cause! This likely due to a bug in the scenario setup!",
                            report.getFault());
                }

                return;
            }

            if (hasFailure) {
                Fault pointFault = report.getFault();
                if (knownFailures.containsKey(pointFault)) {
                    List<Set<Fault>> knownCauses = knownFailures.get(pointFault);
                    var hasKnownCause = hasKnownCause(knownCauses, causes);
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

            if (!hasFailure) {
                var point = report.faultUid;
                if (knownResolutions.containsKey(point)) {
                    List<Set<Fault>> knownCauses = knownResolutions.get(point);
                    var hasKnownCause = hasKnownCause(knownCauses, causes);
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
        StrategyReporter.printHeader("Behavioral Anlaysis", 48, "=");
        StrategyReporter.printHeader("(Internal) Failures", 48, "-");
        for (var entry : knownFailures.entrySet()) {
            var fault = entry.getKey();
            var causes = entry.getValue();
            StrategyReporter.printKeyValue("Failure", fault.toString());
            var i = 0;
            for (var cause : causes) {
                StrategyReporter.printKeyValue("Cause (" + i + ")", cause.toString());
                i++;
            }
        }

        StrategyReporter.printHeader("(Internal) Resiliency", 48, "-");
        for (var entry : knownFailures.entrySet()) {
            var fault = entry.getKey();
            var causes = entry.getValue();
            StrategyReporter.printKeyValue("Failure", fault.toString());
            var i = 0;
            for (var cause : causes) {
                StrategyReporter.printKeyValue("Cause (" + i + ")", cause.toString());
                i++;
            }
        }

        // Don't report in the normal sense
        return null;
    }

}
