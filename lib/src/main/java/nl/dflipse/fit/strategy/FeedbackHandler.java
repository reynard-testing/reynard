package nl.dflipse.fit.strategy;

import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;

public interface FeedbackHandler<T> {
    public T handleFeedback(FaultloadResult result, DynamicAnalysisStore history);
}
