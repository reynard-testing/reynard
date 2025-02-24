package nl.dflipse.fit.strategy.pruners;

import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.HistoricStore;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.TransativeRelation;
import nl.dflipse.fit.strategy.util.TreeAnalysis;

public class HappensBeforePruner implements Pruner, FeedbackHandler<Void> {
    private TreeAnalysis treeAnalysis;
    private TransativeRelation<FaultUid> happensBefore = new TransativeRelation<>();

    @Override
    public Void handleFeedback(FaultloadResult result, HistoricStore history) {
        if (result.isInitial()) {
            treeAnalysis = new TreeAnalysis(result.trace);

            // add the parent-child relations
            for (var pair : treeAnalysis.getRelations()) {
                var parent = pair.getFirst();
                var child = pair.getSecond();
                happensBefore.addRelation(parent, child);
            }

            return null;
        } else {
            // if a single fault causes others to disappear
            // their combination is redundant
            var injectedErrorFaults = result.trace.getFaults()
                    .stream()
                    .filter(fault -> fault.getMode().getType().equals(ErrorFault.FAULT_TYPE))
                    .map(fault -> fault.getUid())
                    .collect(Collectors.toSet());

            var notInjectedFaultUids = result.getNotInjectedFaults()
                    .stream()
                    .map(fault -> fault.getUid())
                    .collect(Collectors.toSet());

            if (injectedErrorFaults.size() == 1 && notInjectedFaultUids.size() > 0) {
                var injectedFault = injectedErrorFaults.iterator().next();

                for (var notInjectedFault : notInjectedFaultUids) {
                    handleHappensBefore(injectedFault, notInjectedFault);
                }
            }
            return null;
        }
    }

    private void handleHappensBefore(FaultUid cause, FaultUid effect) {
        // Case 0: if the cause is a decendant of the effect
        // Then it is trivial, and covered by the Parent-Child pruner

        if (treeAnalysis.isDecendantOf(cause, effect)) {
            System.out.println("Parent-Child happens before: " + cause + " -> " + effect);
            happensBefore.addRelation(cause, effect);
            return;
        }

        var effectParent = treeAnalysis.getParent(effect);
        var causeParent = treeAnalysis.getParent(cause);

        boolean directHappensBefore = treeAnalysis.isEqual(causeParent, effectParent);

        // Case 1: if they share the same parent
        // Then the cause happens before the effect
        if (directHappensBefore) {
            System.out.println("Direct happens before: " + cause + " -> " + effect);
            happensBefore.addRelation(cause, effect);
            return;
        }

        // Case 2: if a ancestor of the cause is the parent of the effect
        // Then the parent cause happens before the effect
        var causeAncestors = treeAnalysis.getParents(cause);
        if (causeAncestors.contains(effectParent)) {
            // get the cause's ancestor whose parent is the effect's parent
            var ancestralCause = causeAncestors.stream()
                    .filter(ancestor -> treeAnalysis.isEqual(treeAnalysis.getParent(ancestor), effectParent))
                    .findFirst()
                    .get();
            // add the relation
            System.out.println("Ancestor happens before: " + ancestralCause + " -> " + effect);
            happensBefore.addRelation(ancestralCause, effect);
            return;
        }

        // Other cases are undefined.
        // For example, the cause's parent is an ancestor of the effect's parent
        // But that should not happen, because then the fault is not contained

        System.out.println("Unknown happens before: " + cause + " -> " + effect);
        happensBefore.addRelation(cause, effect);
    }

    @Override
    public boolean prune(Faultload faultload, HistoricStore history) {
        // if an http error is injected, and its children are also injected, it is
        // redundant.

        Set<FaultUid> errorFaults = faultload
                .getFaults()
                .stream()
                .filter(fault -> fault.getMode().getType().equals(ErrorFault.FAULT_TYPE))
                .map(fault -> fault.getUid())
                .collect(Collectors.toSet());

        boolean isRedundant = Sets.anyPair(errorFaults, (pair) -> {
            var f1 = pair.getFirst();
            var f2 = pair.getSecond();
            return happensBefore.hasTransativeRelation(f1, f2)
                    || happensBefore.hasTransativeRelation(f2, f1);
        });

        return isRedundant;
    }

}
