package nl.dflipse.fit.strategy.analyzers;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;

public class ConditionalFaultDetector implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(ConditionalFaultDetector.class);
    private final Set<FaultUid> pointsInHappyPath = new HashSet<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        var traceFaultUids = result.trace.getFaultUids();

        // --- Happy path ---
        if (result.isInitial()) {
            pointsInHappyPath.addAll(traceFaultUids);
            return;
        }

        Set<FaultUid> expectedPoints = new HashSet<>(pointsInHappyPath);
        expectedPoints.addAll(context.getConditionalForFaultload());

        // Analyse new paths that were not in the happy path
        var appeared = Sets.difference(traceFaultUids, expectedPoints);

        // Report newly found points
        if (!appeared.isEmpty()) {
            // There might be multiple conditions to trigger new fid
            Set<Fault> condition = result.trace.getInjectedFaults();

            for (var newFid : appeared) {
                // We are a retry of a fault that has the same uid, expect for a transient count
                // less than ours
                Set<FaultUid> retriedFaults = expectedPoints.stream()
                        .filter(f -> f.matchesUpToCount(newFid))
                        .filter(f -> f.isTransient())
                        .filter(f -> f.count() == newFid.count() - 1)
                        .collect(Collectors.toSet());

                // edge case: the happy path should only have one count
                // Otherwise, we cannot be sure which one is the retry, especially
                // in the presence of multiple faults.
                boolean happyPathOnlyHasOne = pointsInHappyPath.stream()
                        .filter(f -> f.matchesUpToCount(newFid))
                        .count() == 1;

                // And there is exactly of these
                boolean isRetry = happyPathOnlyHasOne && retriedFaults.size() == 1;

                if (isRetry) {
                    FaultUid retriedFault = Sets.getOnlyElement(retriedFaults);
                    // only distinguish between transient (once) or always
                    FaultUid persistentFault = newFid.asAnyCount();

                    logger.info("Detected retry: {} -> {}", retriedFault, newFid);
                    logger.debug("Turning transient fault into persistent {}", persistentFault);
                    // do not add the transient and the persistent fault
                    context.pruneFaultUidSubset(Set.of(retriedFault, persistentFault));

                    // TODO: handle case were retry is caused by effect of cause
                    // Which the persistent fault will block from happening.
                    // We need to persistently block, STARTING at retry x?

                    // ensure we replace the transient fault with the persistent one
                    Set<Fault> relatedCondition = condition.stream()
                            .filter(f -> !f.uid().matchesUpToCount(persistentFault))
                            .collect(Collectors.toSet());
                    context.reportConditionalFaultUid(relatedCondition, persistentFault);
                } else {
                    context.reportConditionalFaultUid(condition, newFid);
                }
            }
        }
    }
}
