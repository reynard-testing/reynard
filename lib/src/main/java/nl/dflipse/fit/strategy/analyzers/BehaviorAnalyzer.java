package nl.dflipse.fit.strategy.analyzers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;

public class BehaviorAnalyzer implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(BehaviorAnalyzer.class);

    private final Map<Fault, Set<Set<Fault>>> knownFailures = new HashMap<>();
    private final Map<FaultUid, Set<Set<Fault>>> knownResolutions = new HashMap<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
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
                    Set<Set<Fault>> knownCauses = knownFailures.get(pointFault);
                    boolean hasKnownCause = knownCauses.contains(causes);
                    if (!hasKnownCause) {
                        logger.warn("For failure {} discovered new cause: {}", pointFault, causes);
                        knownCauses.add(causes);
                    }
                } else {
                    logger.warn("Detected failure {} for cause: {}", pointFault, causes);
                    Set<Set<Fault>> causesSet = new HashSet<>();
                    causesSet.add(causes);
                    knownFailures.put(pointFault, causesSet);
                }
            }

            if (!hasFailure) {
                var point = report.faultUid;
                if (knownResolutions.containsKey(point)) {
                    Set<Set<Fault>> knownCauses = knownResolutions.get(point);
                    boolean hasKnownCause = knownCauses.contains(causes);
                    if (!hasKnownCause) {
                        logger.warn("Point {} can also recover from cause: {}", point, causes);
                        knownCauses.add(causes);
                    }
                } else {
                    logger.warn("Detected resilient point {} for cause: {}", point, causes);
                    Set<Set<Fault>> causesSet = new HashSet<>();
                    causesSet.add(causes);
                    knownResolutions.put(point, causesSet);
                }
            }
        });
    }

}
