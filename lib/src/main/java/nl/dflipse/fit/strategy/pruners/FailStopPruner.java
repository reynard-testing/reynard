package nl.dflipse.fit.strategy.pruners;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;

public class FailStopPruner implements Pruner, FeedbackHandler<Void> {
    private boolean failed = false;

    @Override
    public Void handleFeedback(FaultloadResult result, DynamicAnalysisStore store) {
        if (!result.passed) {
            failed = true;
        }

        return null;
    }

    @Override
    public boolean prune(Faultload faultload, DynamicAnalysisStore store) {
        if (failed) {
            return true;
        }

        return false;
    }

}
