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
                // var faultParent = treeAnalysis.getParent(injectedFault);

                // // should have a parent, otherwise it is a root fault
                // if (faultParent == null) {
                // return null;
                // }

                // // all not injected faults should be decendants of the parent
                // var parentsDecendants = treeAnalysis.getDecendants(faultParent);
                // if (!Sets.isSubset(parentsDecendants, notInjectedFaultUids)) {
                // return null;
                // }

                for (var notInjectedFault : notInjectedFaultUids) {
                    happensBefore.addRelation(injectedFault, notInjectedFault);
                    System.out.println("Happens before: " + injectedFault + " -> " + notInjectedFault);
                }
            }
            return null;
        }
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
