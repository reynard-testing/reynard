package nl.dflipse.fit.strategy.pruners;

import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.TransativeRelation;

public class HappensBeforePruner implements Pruner, FeedbackHandler<Void> {

    private FaultloadResult initialResult;
    private final TransativeRelation<Fault> happensBefore = new TransativeRelation<>();
    private final Logger logger = LoggerFactory.getLogger(HappensBeforePruner.class);

    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            initialResult = result;
            return null;
        }

        // if a single fault causes others to disappear
        // their combination is redundant
        Set<Fault> injectedErrorFaults = result.trace.getInjectedFaults();

        // if we have a singular cause
        if (injectedErrorFaults.size() != 1) {
            return null;
        }

        Fault cause = Sets.getOnlyElement(injectedErrorFaults);

        var faultsInTrace = result.trace.getFaultUids();

        // TODO: relate to previous subset run?
        // dissappeared faults
        // are those that were in the initial trace,
        // but not in the current trace
        // and not the cause
        var dissappearedFaultPoints = initialResult.trace.getFaultUids()
                .stream()
                .filter(f -> !faultsInTrace.contains(f))
                .filter(f -> !f.equals(cause))
                .collect(Collectors.toSet());

        if (dissappearedFaultPoints.isEmpty()) {
            return null;
        }

        // We have identified a cause, and its effects
        // We can now relate the happens before
        for (var disappearedFaultPoint : dissappearedFaultPoints) {
            handleHappensBefore(cause, disappearedFaultPoint, context);
        }

        // prune the fault subset
        for (var pair : happensBefore.getTransativeRelations()) {
            var parent = pair.getFirst();
            var child = pair.getSecond();

            if (parent == null || child == null) {
                continue;
            }

            // The parent makes the child disappear
            // so we can prune the combination
            context.pruneFaultSubset(Set.of(parent, child));
        }

        return null;
    }

    private void relateHappensBefore(Fault cause, FaultUid effect, FeedbackContext context) {
        for (var mode : context.getFaultModes()) {
            var effectFault = new Fault(effect, mode);
            happensBefore.addRelation(cause, effectFault);
        }
    }

    private void relateHappensBefore(FaultUid cause, FaultUid effect, FeedbackContext context) {
        for (var mode : context.getFaultModes()) {
            var causeFault = new Fault(cause, mode);
            relateHappensBefore(causeFault, effect, context);
        }
    }

    private void handleHappensBefore(Fault cause, FaultUid effect, FeedbackContext context) {
        // If the cause is a decendant of the effect
        // Then it is trivial, and covered by the Parent-Child pruner
        FaultUid causeUid = cause.uid();
        if (initialResult.trace.isDecendantOf(causeUid, effect)) {
            logger.info("Parent-Child happens before: " + cause + " -> " + effect);
            relateHappensBefore(cause, effect, context);
            return;
        }

        logger.info("Direct happens before: " + cause + " -> " + effect);
        relateHappensBefore(cause, effect, context);

        // If a ancestor of the cause is the parent of the effect
        // Then the parental causes happens before the effect
        var effectParent = initialResult.trace.getParent(effect);
        var causeAncestors = initialResult.trace.getParents(causeUid);
        if (causeAncestors.contains(effectParent)) {
            for (var ancestralCause : causeAncestors) {
                if (ancestralCause == null) {
                    break;
                }

                if (ancestralCause.equals(effectParent) || ancestralCause.equals(effect)) {
                    break;
                }

                // add the relation
                // TODO: combine with error propogation?
                logger.info("Ancestor happens before: " + ancestralCause + " -> " + effect);
                // relateHappensBefore(ancestralCause, effect, context);
            }

            return;
        }
    }

    @Override
    public boolean prune(Faultload faultload) {
        // if an error is injected, and its children are also injected, it is
        // redundant.

        boolean isRedundant = Sets.anyPair(faultload.faultSet(), (pair) -> {
            var f1 = pair.getFirst();
            var f2 = pair.getSecond();
            return happensBefore.areRelated(f1, f2);
        });

        return isRedundant;
    }

}
