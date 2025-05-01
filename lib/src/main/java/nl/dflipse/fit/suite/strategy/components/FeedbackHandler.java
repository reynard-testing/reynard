package io.github.delanoflipse.fit.suite.strategy.components;

import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;

public interface FeedbackHandler {
    public void handleFeedback(FaultloadResult result, FeedbackContext context);
}
