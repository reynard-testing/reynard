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
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.components.FeedbackContext;
import nl.dflipse.fit.strategy.components.FeedbackHandler;
import nl.dflipse.fit.strategy.components.PruneContext;
import nl.dflipse.fit.strategy.components.PruneDecision;
import nl.dflipse.fit.strategy.components.Pruner;

public class DynamicReductionPruner implements Pruner, FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(DynamicReductionPruner.class);

    private final List<Map<FaultUid, Behaviour>> behavioursSeen = new ArrayList<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        // update behaviours seen
        Map<FaultUid, Behaviour> behaviourMap = new HashMap<>();
        for (var report : result.trace.getReports()) {
            behaviourMap.put(report.faultUid, report.getBehaviour());
        }

        behavioursSeen.add(behaviourMap);
    }

    @Override
    public PruneDecision prune(Faultload faultload, PruneContext ctx) {
        Set<Behaviour> expected = ctx.getExpectedBehaviours(faultload.faultSet());
        Map<FaultUid, Behaviour> behaviourByUid = expected.stream()
                .collect(HashMap::new, (m, b) -> m.put(b.uid(), b), HashMap::putAll);

        // for all causes
        for (var expectedBehaviour : expected) {
            boolean found = false;

            // there should exist an earlier run
            for (var observed : behavioursSeen) {
                var allEffectsSeen = true;

                // that has all dependents with the same expected behaviour
                List<Behaviour> effects = expected.stream()
                        .filter(f -> f.uid().hasParent() && f.uid().getParent().matches(expectedBehaviour.uid()))
                        .toList();

                for (var effect : effects) {
                    if (!expected.contains(effect)) {
                        continue;
                    }

                    boolean hasObservedEffect = observed.containsKey(effect.uid());
                    if (!hasObservedEffect) {
                        allEffectsSeen = false;
                        break;
                    }

                    boolean hasBehaviour = behaviourByUid.containsKey(effect.uid());
                    Behaviour observedBehaviour = hasBehaviour
                            ? behaviourByUid.get(effect.uid())
                            : null;

                    if (!observedBehaviour.matches(expectedBehaviour)) {
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
