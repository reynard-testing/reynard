package nl.dflipse.fit.strategy.pruners;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackHandler;

public class FailStopPruner implements Pruner, FeedbackHandler<Void> {
    private boolean failed = false;

    @Override
    public Void handleFeedback(FaultloadResult result) {
        if (!result.passed) {
            failed = true;
        }

        return null;
    }

    @Override
    public boolean prune(Faultload faultload) {
        if (failed) {
            return true;
        }

        return false;
    }

}
