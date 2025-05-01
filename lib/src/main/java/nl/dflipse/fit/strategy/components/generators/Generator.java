package io.github.delanoflipse.fit.strategy.components.generators;

import io.github.delanoflipse.fit.faultload.Faultload;
import io.github.delanoflipse.fit.strategy.components.FeedbackContext;

public abstract class Generator extends FeedbackContext {
    public abstract Faultload generate();

    public void prune() {
    };
}
