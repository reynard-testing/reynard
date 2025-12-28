package dev.reynard.junit.strategy.components;

import dev.reynard.junit.strategy.FaultloadResult;

public interface FeedbackHandler {
    public void handleFeedback(FaultloadResult result, FeedbackContext context);
}
