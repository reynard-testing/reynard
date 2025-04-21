package nl.dflipse.fit.strategy.components;

import nl.dflipse.fit.strategy.FaultloadResult;

public interface FeedbackHandler {
    public void handleFeedback(FaultloadResult result, FeedbackContext context);
}
