package io.github.delanoflipse.fit.strategy.components;

import io.github.delanoflipse.fit.strategy.FaultloadResult;

public interface FeedbackHandler {
    public void handleFeedback(FaultloadResult result, FeedbackContext context);
}
