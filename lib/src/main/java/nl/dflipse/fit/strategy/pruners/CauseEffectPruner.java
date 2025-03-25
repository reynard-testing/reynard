package nl.dflipse.fit.strategy.pruners;

import java.util.HashMap;
import java.util.Map;
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

// TODO: rename
public class HappensBeforePruner implements Pruner, FeedbackHandler<Void> {

    private FaultloadResult initialResult;
    private final Map<FaultUid, Set<Set<Fault>>> effectCauseMapping = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(HappensBeforePruner.class);

    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            initialResult = result;
            return null;
        }

        // if (a set of) fault(s) causes another fault to disappear
        // then the cause(s) happens before the effect
        Set<Fault> injectedErrorFaults = result.trace.getInjectedFaults();

        // if we have a singular cause
        if (injectedErrorFaults.isEmpty()) {
            return null;
        }

        var faultsInTrace = result.trace.getFaultUids();

        // dissappeared faults
        // are those that were in the initial trace,
        // but not in the current trace
        // and not the cause
        Set<FaultUid> injectedFaultPoints = injectedErrorFaults.stream().map(Fault::uid).collect(Collectors.toSet());

        Set<FaultUid> dissappearedFaultPoints = initialResult.trace.getFaultUids()
                .stream()
                .filter(f -> !faultsInTrace.contains(f))
                .filter(f -> !injectedFaultPoints.contains(f))
                .collect(Collectors.toSet());

        if (dissappearedFaultPoints.isEmpty()) {
            return null;
        }

        // We have identified a cause, and its effects
        // We can now relate the happens before
        for (var disappearedFaultPoint : dissappearedFaultPoints) {
            handleHappensBefore(injectedErrorFaults, disappearedFaultPoint, context);
        }

        // prune the fault subset
        for (var causeAndEffect : effectCauseMapping.entrySet()) {
            var effect = causeAndEffect.getKey();
            var possibleCauses = causeAndEffect.getValue();

            var effectedPoints = context.getFaultUids().stream()
                    .filter(f -> f.matchesUpToCount(effect))
                    .collect(Collectors.toSet());

            // For every related fault injection point
            for (var point : effectedPoints) {
                // and every fault mode
                for (var mode : context.getFaultModes()) {
                    var fault = new Fault(point, mode);

                    // and each possible cause for the dissapeareance
                    // of the fault
                    for (var cause : possibleCauses) {
                        // the combination is redundant
                        var redundant = Sets.plus(cause, fault);
                        context.pruneFaultSubset(redundant);
                    }
                }
            }
        }

        return null;
    }

    private boolean hasAlready(Set<Fault> cause, FaultUid effect) {
        if (!effectCauseMapping.containsKey(effect)) {
            return false;
        }

        Set<Set<Fault>> possibleCauses = effectCauseMapping.get(effect);
        return possibleCauses.stream()
                .anyMatch(pc -> Sets.isSubsetOf(pc, cause));

    }

    private void handleHappensBefore(Set<Fault> cause, FaultUid effect, FeedbackContext context) {
        logger.info("Found dependent effect: " + cause + " hides " + effect);

        // Also discregard any counts of the effect

        if (hasAlready(cause, effect)) {
            return;
        }

        // Store as the fid (disregarding count)
        effectCauseMapping.put(effect.asAnyCount(), Set.of(cause));

    }

    @Override
    public boolean prune(Faultload faultload) {
        // if an error is injected, and its children are also injected, it is
        // redundant.

        Set<Fault> faultset = faultload.faultSet();

        // for each fault in the faultload
        Set<FaultUid> relatedUidsInFaultload = faultset.stream()
                // given their uid
                .map(Fault::uid)
                // diregard the count
                .map(FaultUid::asAnyCount)
                // that has a known redundancy
                .filter(effectCauseMapping::containsKey)
                .collect(Collectors.toSet());

        boolean isRedundant = relatedUidsInFaultload.stream()
                // for any related fid's causes
                .anyMatch(f -> effectCauseMapping.get(f).stream()
                        // if the cause is a subset of the faultset
                        // it is redundant
                        .anyMatch(cause -> Sets.isSubsetOf(faultset, cause)));

        return isRedundant;
    }

}
