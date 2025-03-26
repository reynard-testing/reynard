package nl.dflipse.fit.strategy;

public interface FeedbackHandler {
    public void handleFeedback(FaultloadResult result, FeedbackContext context);
}
