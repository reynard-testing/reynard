package nl.dflipse.fit.strategy.pruners;

import java.util.Set;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.TransativeRelation;

public class ParentChildPruner implements Pruner, FeedbackHandler {
    private TransativeRelation<FaultUid> happensBefore = new TransativeRelation<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        for (var pair : result.trace.getParentsAndChildren()) {
            var parent = pair.getFirst();
            var child = pair.getSecond();
            happensBefore.addRelation(parent, child);
        }

        // TODO: is it necessary to prune the combination here?
        // Can we just use the prune fn?
        for (var pair : happensBefore.getTransativeRelations()) {
            var parent = pair.getFirst();
            var child = pair.getSecond();

            if (parent == null || child == null) {
                continue;
            }

            // The parent makes the child disappear
            // so we can prune the combination
            context.reportExclusionOfFaultUidByUid(Set.of(parent), child);
            // We can also exclude the child for any #inf
            FaultUid anyCount = parent.asAnyCount();
            context.reportExclusionOfFaultUidByUid(Set.of(anyCount), child);
        }
    }

    @Override
    public PruneDecision prune(Faultload faultload) {
        // if an http error is injected, and its children are also injected, it is
        // redundant.

        Set<FaultUid> errorFaults = faultload.getFaultUids();

        boolean isRedundant = Sets.anyPair(errorFaults, (pair) -> {
            var f1 = pair.getFirst();
            var f2 = pair.getSecond();
            return happensBefore.areRelated(f1, f2);
        });

        if (isRedundant) {
            return PruneDecision.PRUNE_SUBTREE;
        } else {
            return PruneDecision.KEEP;
        }
    }

}
