package nl.dflipse.fit.strategy.pruners;

import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.TransativeRelation;

public class HappensBeforePruner implements Pruner, FeedbackHandler<Void> {
    private FaultloadResult initialResult;
    private final TransativeRelation<FaultUid> happensBefore = new TransativeRelation<>();

    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            initialResult = result;

            // add the parent-child relations
            // for (var pair : result.trace.getRelations()) {
            // var parent = pair.getFirst();
            // var child = pair.getSecond();
            // happensBefore.addRelation(parent, child);
            // }

            return null;
        }

        // if a single fault causes others to disappear
        // their combination is redundant
        Set<Fault> injectedErrorFaults = result.trace.getInjectedFaults()
                .stream()
                .filter(fault -> fault.mode().getType().equals(ErrorFault.FAULT_TYPE))
                .collect(Collectors.toSet());

        if (injectedErrorFaults.size() != 1) {
            return null;
        }

        // if we have a singular cause
        Fault cause = Sets.getOnlyElement(injectedErrorFaults);

        var faultsInTrace = result.trace.getFaultUids();
        var dissappearedFaults = initialResult.trace.getFaultUids()
                .stream()
                .filter(f -> !faultsInTrace.contains(f))
                .filter(f -> !f.equals(cause))
                .filter(f -> !happensBefore.hasTransativeRelation(cause.uid(), f))
                .collect(Collectors.toSet());

        if (dissappearedFaults.isEmpty()) {
            return null;
        }

        // that makes others disappear
        for (var notInjectedFault : dissappearedFaults) {
            handleHappensBefore(cause, notInjectedFault, context);
        }

        // TODO: prune all _transative_ relations in the context
        for (var pair : happensBefore.getTransativeRelations()) {
            var parent = pair.getFirst();
            var child = pair.getSecond();

            if (parent == null || child == null) {
                continue;
            }

            context.pruneFaultUidSubset(Set.of(parent, child));
        }

        return null;
    }

    private void handleHappensBefore(Fault cause, FaultUid effect, FeedbackContext context) {
        // Case 0: if the cause is a decendant of the effect
        // Then it is trivial, and covered by the Parent-Child pruner
        FaultUid causeUid = cause.uid();
        if (initialResult.trace.isDecendantOf(causeUid, effect)) {
            System.out.println("Parent-Child happens before: " + cause + " -> " + effect);
            // happensBefore.addRelation(cause, effect);
            context.pruneFaultUidSubset(Set.of(causeUid, effect));
            return;
        }

        var effectParent = initialResult.trace.getParent(effect);
        var causeParent = initialResult.trace.getParent(causeUid);

        boolean directHappensBefore = initialResult.trace.isEqual(causeParent, effectParent);

        // Case 1: if they share the same parent
        // Then the cause happens before the effect
        if (directHappensBefore) {
            System.out.println("Direct happens before: " + cause + " -> " + effect);
            happensBefore.addRelation(causeUid, effect);
            return;
        }

        // Case 2: if a ancestor of the cause is the parent of the effect
        // Then the parent cause happens before the effect
        var causeAncestors = initialResult.trace.getParents(causeUid);
        if (causeAncestors.contains(effectParent)) {
            System.out.println("Direct happens before: " + cause + " -> " + effect);
            happensBefore.addRelation(causeUid, effect);
            context.pruneFaultUidSubset(Set.of(causeUid, effect));

            for (var ancestralCause : causeAncestors) {
                if (ancestralCause.equals(effectParent) || ancestralCause.equals(effect)) {
                    break;
                }

                // add the relation
                System.out.println("Ancestor happens before: " + ancestralCause + " -> " + effect);
                happensBefore.addRelation(ancestralCause, effect);
                return;
            }
        }

        // Other cases are undefined.
        // For example, the cause's parent is an ancestor of the effect's parent
        // But that should not happen, because then the fault is not contained

        System.out.println("Unknown happens before: " + cause + " -> " + effect);
        happensBefore.addRelation(causeUid, effect);
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
            return happensBefore.hasTransativeRelation(f1, f2)
                    || happensBefore.hasTransativeRelation(f2, f1);
        });

        return isRedundant;
    }

}
