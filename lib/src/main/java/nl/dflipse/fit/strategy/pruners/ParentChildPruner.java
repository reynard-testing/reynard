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
import nl.dflipse.fit.strategy.util.TreeAnalysis;

public class ParentChildPruner implements Pruner, FeedbackHandler<Void> {
    private TreeAnalysis treeAnalysis;

    @Override
    public Void handleFeedback(FaultloadResult result, HistoricStore history) {
        if (result.isInitial()) {
            treeAnalysis = new TreeAnalysis(result.trace);
        }

        return null;
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
            return treeAnalysis.isDecendantOf(f1, f2)
                    || treeAnalysis.isDecendantOf(f2, f1);
        });

        return isRedundant;
    }

}
