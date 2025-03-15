package nl.dflipse.fit.strategy.pruners;

import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;

public class ParentChildPruner implements Pruner, FeedbackHandler<Void> {
    private FaultloadResult initialResult;

    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        // TODO: handle other results too, as new FID might appear!
        if (result.isInitial()) {
            initialResult = result;
        }

        for (var pair : result.trace.getParentsAndTransativeChildren()) {
            var parent = pair.getFirst();
            var child = pair.getSecond();

            if (parent == null || child == null) {
                continue;
            }

            context.pruneFaultUidSubset(Set.of(parent, child));
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
                .filter(fault -> fault.mode().getType().equals(ErrorFault.FAULT_TYPE))
                .map(fault -> fault.uid())
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
