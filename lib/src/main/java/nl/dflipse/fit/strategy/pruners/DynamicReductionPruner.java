package nl.dflipse.fit.strategy.pruners;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.HistoricStore;

public class DynamicReductionPruner implements Pruner, FeedbackHandler<Void> {

    private Map<FaultUid, Set<FaultUid>> causalMap = new HashMap<>();
    private Map<FaultUid, Set<Integer>> behavioursSeen = new HashMap<>();
    private Map<FaultUid, Integer> happyPath = new HashMap<>();

    @Override
    public Void handleFeedback(FaultloadResult result, HistoricStore history) {
        if (result.isInitial()) {
            for (var report : result.trace.getReports()) {
                FaultUid uid = report.faultUid;
                int behaviour = report.response.status;
                happyPath.put(uid, behaviour);
            }
        }

        // update causal map
        for (var parentChild : result.trace.getRelations()) {
            FaultUid parent = parentChild.first();
            FaultUid child = parentChild.second();

            if (!causalMap.containsKey(parent)) {
                causalMap.put(parent, new HashSet<>());
            }

            causalMap.get(parent).add(child);
        }

        // update behaviours seen
        for (var report : result.trace.getReports()) {
            FaultUid uid = report.faultUid;
            // TODO: the behaviours should correspond to the SAME trace
            int behaviour = report.response.status;

            if (!behavioursSeen.containsKey(uid)) {
                behavioursSeen.put(uid, new HashSet<>());
            }

            behavioursSeen.get(uid).add(behaviour);
        }
        return null;
    }

    @Override
    public boolean prune(Faultload faultload, HistoricStore history) {
        Map<FaultUid, Fault> faultsByFaultUid = faultload.faultSet()
                .stream()
                .collect(Collectors.toMap(Fault::getUid, Function.identity()));

        boolean shouldPrune = true;

        for (var cause : causalMap.keySet()) {
            var allEffectsSeen = true;

            for (var effect : causalMap.get(cause)) {
                int expectedOutcome = happyPath.get(effect);

                // If we are supposed to inject a fault
                if (faultsByFaultUid.containsKey(effect)) {
                    Fault fault = faultsByFaultUid.get(effect);
                    if (fault.getMode().getType() == ErrorFault.FAULT_TYPE) {
                        expectedOutcome = Integer.parseInt(fault.getMode().getArgs().get(0));
                    }
                }

                // if we haven't seen the expected outcome for this effect
                // then we should not prune
                if (!behavioursSeen.get(effect).contains(expectedOutcome)) {
                    allEffectsSeen = false;
                    break;
                }
            }

            // if we have not seen all effects, then we should not prune
            if (!allEffectsSeen) {
                shouldPrune = false;
                break;
            }
        }

        if (shouldPrune) {
            return true;
        }

        return shouldPrune;
    }

}
