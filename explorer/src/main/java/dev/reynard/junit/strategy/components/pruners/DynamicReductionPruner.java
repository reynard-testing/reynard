package dev.reynard.junit.strategy.components.pruners;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.reynard.junit.faultload.Behaviour;
import dev.reynard.junit.faultload.Faultload;
import dev.reynard.junit.strategy.components.PruneContext;
import dev.reynard.junit.strategy.components.PruneDecision;
import dev.reynard.junit.strategy.components.Pruner;
import dev.reynard.junit.strategy.util.Env;

public class DynamicReductionPruner implements Pruner {
    private final Logger logger = LoggerFactory.getLogger(DynamicReductionPruner.class);

    private final boolean enabled;

    public DynamicReductionPruner() {
        this.enabled = Env.getEnvBool(Env.Keys.USE_SER);
    }

    @Override
    public PruneDecision prune(Faultload faultload, PruneContext ctx) {
        if (!enabled) {
            return PruneDecision.KEEP;
        }

        Set<Behaviour> expected = ctx.getExpectedBehaviours(faultload.faultSet());

        // for all causes
        for (var cause : expected) {
            // and its effects
            List<Behaviour> effects = expected.stream()
                    .filter(b -> b.uid().hasParent())
                    .filter(b -> b.uid().getParent().matches(cause.uid()))
                    .toList();

            if (effects.isEmpty()) {
                continue;
            }

            // We check if there is a historic result that has all the effects
            boolean found = ctx.getHistoricResults()
                    .stream()
                    .anyMatch(historicResult -> {
                        return Behaviour.isSubsetOf(effects, historicResult.second());
                    });

            if (!found) {
                return PruneDecision.KEEP;
            }

        }

        return PruneDecision.PRUNE;
    }

}
