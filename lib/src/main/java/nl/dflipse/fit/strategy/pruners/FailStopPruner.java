package nl.dflipse.fit.strategy.pruners;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;

public class FailStopPruner implements Pruner, FeedbackHandler {
    private boolean failed = false;

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (!result.passed) {
            failed = true;
        }
    }

    @Override
    public PruneDecision prune(Faultload faultload) {
        if (failed) {
            return PruneDecision.PRUNE_SUBTREE;
        }

        return PruneDecision.KEEP;
    }

}
