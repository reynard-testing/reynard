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

public class ConditionalFaultDetector implements FeedbackHandler<Void> {
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
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        var traceFaultUids = result.trace.getFaultUids();
        // --- Happy path ---
        if (result.isInitial()) {
            pointsInHappyPath.addAll(traceFaultUids);
            return null;
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
                    Set<FaultUid> relatedFaults = getUpToCount(fid);
                    FaultUid anyRetryFault = fid.asAnyCount();
                    logger.debug("Detected retry: Faults {} causes {}", condition, fid);

                    // Multiple retries might be present
                    if (processed.contains(anyRetryFault)) {
                        break;
                    }

                    for (var other : relatedFaults) {
                        logger.debug("Turning transient fault {} into {}", other, anyRetryFault);
                        context.pruneFaultUidSubset(Set.of(other, anyRetryFault));
                    }

                    // parts of the condition that are not related to replace fault
                    // TODO: fix issue with same fuids in faultload!
                    Set<Fault> relatedCondition = condition.stream()
                            .filter(f -> relatedFaults.contains(f.uid()))
                            .filter(f -> f.uid().equals(anyRetryFault))
                            .collect(Collectors.toSet());
                    Set<Fault> modifiedCondition = Sets.difference(condition, relatedCondition);
                    context.reportConditionalFaultUid(modifiedCondition, anyRetryFault);
                    processed.add(anyRetryFault);
                } else {
                    context.reportConditionalFaultUid(condition, fid);
                }
            }
        }

        return null;
    }
}
