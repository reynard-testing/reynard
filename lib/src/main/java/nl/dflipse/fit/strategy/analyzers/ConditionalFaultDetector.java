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

    private Set<FaultUid> getUpToCount(FaultUid fid) {
        return pointsInHappyPath.stream()
                .filter(f -> f.matchesUpToCount(fid))
                .collect(Collectors.toSet());
    }

    private boolean isRetry(FaultUid newFault) {
        Set<FaultUid> relatedFaults = getUpToCount(newFault);
        // TODO: what if happy path contains >1 count?
        // Parallel or repetitive requests can happen.

        return !relatedFaults.isEmpty();
    }

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        var traceFaultUids = result.trace.getFaultUids();
        // --- Happy path ---
        if (result.isInitial()) {
            pointsInHappyPath.addAll(traceFaultUids);
            return;
        }

        // Analyse new paths that were not in the happy path
        var appeared = Sets.difference(traceFaultUids, pointsInHappyPath);

        // Report newly found points
        if (!appeared.isEmpty()) {
            // There might be multiple conditions to trigger new fid
            Set<Fault> condition = result.trace.getInjectedFaults();
            Set<FaultUid> processed = new HashSet<>();

            for (var fid : appeared) {
                if (isRetry(fid)) {
                    // only distinguish between transient (once) or always
                    FaultUid persistentFault = fid.asAnyCount();
                    Set<FaultUid> transientFaults = getUpToCount(fid);
                    logger.info("Detected retry: {}", fid);

                    // Multiple retries might be present
                    if (processed.contains(persistentFault)) {
                        continue;
                    }

                    for (var other : transientFaults) {
                        logger.debug("Turning transient fault {}\ninto persistent {}", other, persistentFault);
                        context.pruneFaultUidSubset(Set.of(other, persistentFault));
                    }

                    // parts of the condition that are not related to
                    // TODO: handle case were retry is caused by effect of cause
                    // We need to persistently block, STARTING at retry x?
                    Set<Fault> relatedCondition = condition.stream()
                            .filter(f -> !f.uid().matchesUpToCount(persistentFault))
                            .collect(Collectors.toSet());
                    context.reportConditionalFaultUid(relatedCondition, persistentFault);
                    processed.add(persistentFault);
                } else {
                    context.reportConditionalFaultUid(condition, fid);
                }
            }
        }
    }
}
