package nl.dflipse.fit.strategy.analyzers;

import java.util.HashSet;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;

public class ConditionalFaultDetector implements FeedbackHandler<Void> {
    private final Set<FaultUid> pointsInHappyPath = new HashSet<>();

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

            for (var fid : appeared) {
                context.reportConditionalFaultUid(condition, fid);
            }
        }

        return null;
    }
}
