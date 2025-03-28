package nl.dflipse.fit.strategy.pruners;

import java.util.HashMap;
import java.util.HashSet;
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

/**
 * Pruner that prunes faults that are redundant due to cause-effect
 * relationships
 * I.e. if a fault is injected, and another fault disappears,
 * s set of fault causes the disappearance of the fault (the effect)
 */
public class CauseEffectPruner implements Pruner, FeedbackHandler {

    private final Set<FaultUid> pointsInHappyPath = new HashSet<>();
    private final Map<FaultUid, Set<Set<Fault>>> effectCauseMapping = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(CauseEffectPruner.class);

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        var faultsInTrace = result.trace.getFaultUids();

        if (result.isInitial()) {
            pointsInHappyPath.addAll(faultsInTrace);
            return;
        }

        // if (a set of) fault(s) causes another fault to disappear
        // then the cause(s) happens before the effect
        Set<Fault> injectedErrorFaults = result.trace.getInjectedFaults();
        Set<FaultUid> injectedFaultPoints = injectedErrorFaults.stream().map(Fault::uid).collect(Collectors.toSet());

        // if we have a singular cause
        if (injectedErrorFaults.isEmpty()) {
            return;
        }

        // dissappeared faults
        // are those that were in the initial trace
        // or that we expect given the preconditions and the expected faults
        // but not in the current trace
        // and not the cause
        Set<FaultUid> expectedPoints = new HashSet<>(pointsInHappyPath);
        expectedPoints.addAll(context.getConditionalForFaultload());

        Set<FaultUid> dissappearedFaultPoints = expectedPoints.stream()
                .filter(f -> !faultsInTrace.contains(f))
                .filter(f -> !injectedFaultPoints.contains(f))
                .collect(Collectors.toSet());

        if (dissappearedFaultPoints.isEmpty()) {
            return;
        }

        // We have identified a cause, and its effects
        // We can now relate the happens before
        for (var disappearedFaultPoint : dissappearedFaultPoints) {
            // TODO: handle case where the happy path contains two counts
            // And the first causes the second to dissappear
            handleHappensBefore(injectedErrorFaults, disappearedFaultPoint, context);
        }

        // prune the fault subset
        for (var causeAndEffect : effectCauseMapping.entrySet()) {
            var effect = causeAndEffect.getKey();
            var possibleCauses = causeAndEffect.getValue();

            // For every related fault injection point
            // and every fault mode
            for (var mode : context.getFaultModes()) {
                var fault = new Fault(effect, mode);

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
        effectCauseMapping.put(effect, Set.of(cause));

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
