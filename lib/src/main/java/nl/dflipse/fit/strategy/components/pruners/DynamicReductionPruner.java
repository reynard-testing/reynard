package nl.dflipse.fit.strategy.components.pruners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.components.PruneContext;
import nl.dflipse.fit.strategy.components.PruneDecision;
import nl.dflipse.fit.strategy.components.Pruner;

public class DynamicReductionPruner implements Pruner {
    private final Logger logger = LoggerFactory.getLogger(DynamicReductionPruner.class);

    private Behaviour find(List<Behaviour> observed, FaultUid uid) {
        for (var entry : observed) {
            if (entry.uid().matches(uid)) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public PruneDecision prune(Faultload faultload, PruneContext ctx) {
        Set<Behaviour> expected = ctx.getExpectedBehaviours(faultload.faultSet());

        // for all causes
        for (var expectedBehaviour : expected) {
            boolean found = false;

            // that has all dependents with the same expected behaviour
            List<Behaviour> effects = expected.stream()
                    .filter(f -> f.uid().hasParent() && f.uid().getParent().matches(expectedBehaviour.uid()))
                    .toList();

            // there should exist an earlier run
            for (var historicResult : ctx.getHistoricResults()) {
                var allEffectsSeen = true;
                var observed = historicResult.second();

                for (var effect : effects) {
                    Behaviour observedEffect = find(observed, effect.uid());
                    if (observedEffect == null) {
                        allEffectsSeen = false;
                        break;
                    }

                    if (!effect.matches(observedEffect)) {
                        allEffectsSeen = false;
                        break;
                    }
                }

                // if we have seen all effects before, then we might prune
                if (allEffectsSeen) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return PruneDecision.KEEP;
            }
        }

        logger.info("Found redundant fautload=" + faultload.readableString());
        return PruneDecision.PRUNE;
    }

}
