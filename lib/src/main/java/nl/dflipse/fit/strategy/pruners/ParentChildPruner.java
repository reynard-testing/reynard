package nl.dflipse.fit.strategy.pruners;

import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;

public class ParentChildPruner implements Pruner, FeedbackHandler<Void> {
    private FaultloadResult initialResult;

    @Override
    public Void handleFeedback(FaultloadResult result) {
        if (result.isInitial()) {
            initialResult = result;
        }

        return null;
    }

    @Override
    public boolean prune(Faultload faultload) {
        // if an http error is injected, and its children are also injected, it is
        // redundant.

        Set<FaultUid> errorFaults = faultload
                .faultSet()
                .stream()
                .filter(fault -> fault.getMode().getType().equals(ErrorFault.FAULT_TYPE))
                .map(fault -> fault.getUid())
                .collect(Collectors.toSet());

        boolean isRedundant = Sets.anyPair(errorFaults, (pair) -> {
            var f1 = pair.getFirst();
            var f2 = pair.getSecond();
            return initialResult.trace.isDecendantOf(f1, f2)
                    || initialResult.trace.isDecendantOf(f2, f1);
        });

        return isRedundant;
    }

}
