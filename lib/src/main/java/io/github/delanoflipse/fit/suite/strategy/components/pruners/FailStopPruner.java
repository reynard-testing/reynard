package io.github.delanoflipse.fit.suite.strategy.components.pruners;

import io.github.delanoflipse.fit.suite.faultload.Faultload;
import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.components.PruneContext;
import io.github.delanoflipse.fit.suite.strategy.components.PruneDecision;
import io.github.delanoflipse.fit.suite.strategy.components.Pruner;

public class FailStopPruner implements Pruner, FeedbackHandler {
    private boolean failed = false;

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (!result.passed) {
            failed = true;
        }
    }

    @Override
    public PruneDecision prune(Faultload faultload, PruneContext context) {
        if (failed) {
            return PruneDecision.PRUNE_SUPERSETS;
        }

        return PruneDecision.KEEP;
    }

}
