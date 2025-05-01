package io.github.delanoflipse.fit.suite.strategy.components.generators;

import io.github.delanoflipse.fit.suite.faultload.Faultload;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;

public abstract class Generator extends FeedbackContext {
    public abstract Faultload generate();

    public void prune() {
    };
}
