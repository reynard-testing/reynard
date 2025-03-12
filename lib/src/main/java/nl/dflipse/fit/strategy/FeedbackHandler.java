package nl.dflipse.fit.strategy;

public interface FeedbackHandler<T> {
    public T handleFeedback(FaultloadResult result);
}
