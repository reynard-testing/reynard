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
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FailureMode;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.Reporter;
import nl.dflipse.fit.strategy.StrategyReporter;
import nl.dflipse.fit.strategy.store.SubsetStore;
import nl.dflipse.fit.strategy.util.Pair;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;
import nl.dflipse.fit.trace.tree.TraceReport;
import nl.dflipse.fit.trace.tree.TraceResponse;

public class BehaviorAnalyzer implements FeedbackHandler, Reporter {
    private final Logger logger = LoggerFactory.getLogger(BehaviorAnalyzer.class);

    // TODO: equality checks (wrt masks)
    private final Map<Fault, SubsetStore<Fault>> knownFailures = new LinkedHashMap<>();
    private final Map<FaultUid, SubsetStore<Fault>> knownResolutions = new LinkedHashMap<>();
    private final Map<FaultUid, TraceResponse> happyPath = new LinkedHashMap<>();
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
                    Fault fault = childReport.getRepresentativeFault();

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
                    logger.warn("Detected failure {} with no cause! This can be indicative of a bug!",
                            report.getRepresentativeFault());
                }

                if (hasAlteredResponse) {
                    logger.warn(
                            "Detected altered response {} at {} with no cause! This can be indicative of a bug!",
                            report.response, report.faultUid);
                }

                return;
            }

            if (hasFailure) {
                Fault pointFault = report.getRepresentativeFault();
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

    private Pair<List<Set<Fault>>, List<Set<FaultUid>>> simplify(List<Set<Fault>> sets) {
        List<Set<Fault>> faultSets = new ArrayList<>();
        List<Set<FaultUid>> faultUidSets = new ArrayList<>();

        Set<Integer> toSkip = new HashSet<>();

        for (int i = 0; i < sets.size(); i++) {
            if (toSkip.contains(i)) {
                continue;
            }

            var subset = sets.get(i);
            Set<FaultUid> faultUids = Faultload.getFaultUids(subset);

            Map<FaultUid, Set<FailureMode>> represented = new HashMap<>();
            for (var uid : faultUids) {
                represented.put(uid, new HashSet<>());
            }
            Set<Integer> skipIfFound = new HashSet<>();

            // if for every element in the subset,
            // all faults of all failure modes are present
            for (int j = i; j < sets.size(); j++) {
                if (toSkip.contains(j)) {
                    continue;
                }
                var other = sets.get(j);
                Set<FaultUid> otherUids = Faultload.getFaultUids(other);

                if (!otherUids.equals(faultUids)) {
                    continue;
                }

                skipIfFound.add(j);
                for (var fault : other) {
                    represented.get(fault.uid()).add(fault.mode());
                }
            }

            boolean allRepresented = true;
            for (var entry : represented.entrySet()) {
                var modes = entry.getValue();

                if (modes.size() != failureModes.size()) {
                    allRepresented = false;
                    break;
                }
            }

            if (allRepresented) {
                faultUidSets.add(faultUids);
                toSkip.addAll(skipIfFound);
            } else {
                faultSets.add(subset);
            }
        }

        return Pair.of(faultSets, faultUidSets);

    }

    @Override
    public Map<String, String> report() {
        StrategyReporter.printNewline();
        StrategyReporter.printHeader("Behavioral Anlaysis", 48, "=");

        StrategyReporter.printNewline();
        StrategyReporter.printHeader("Happy Path", 48, "-");
        for (var entry : happyPath.entrySet()) {
            var point = entry.getKey();
            var response = entry.getValue();
            StrategyReporter.printNewline();
            StrategyReporter.printKeyValue("Point", point.toString());
            String bodyLimited = response.body.replace("\n", "");
            if (response.body.length() > 100) {
                bodyLimited = response.body.substring(0, 97) + "...";
            }
            StrategyReporter.printKeyValue("Response", "[" + response.status + "]" + bodyLimited);
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
            var store = entry.getValue();

            StrategyReporter.printNewline();
            StrategyReporter.printKeyValue("Failure", fault.toString());

            var simplified = simplify(store.getSets());

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

            var simplified = simplify(store.getSets());

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
