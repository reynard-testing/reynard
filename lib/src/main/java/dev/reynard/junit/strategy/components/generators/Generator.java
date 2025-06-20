package dev.reynard.junit.strategy.components.generators;

import dev.reynard.junit.faultload.Faultload;
import dev.reynard.junit.strategy.components.FeedbackContext;

public abstract class Generator extends FeedbackContext {
    public abstract Faultload generate();

    public void prune() {
    };
}
