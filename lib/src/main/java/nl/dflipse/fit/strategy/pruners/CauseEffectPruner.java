package nl.dflipse.fit.strategy.pruners;

import java.util.HashSet;
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
import nl.dflipse.fit.strategy.store.ConditionalStore;

/**
 * Pruner that prunes faults that are redundant due to cause-effect
 * relationships
 * I.e. if a fault is injected, and another fault disappears,
 * s set of fault causes the disappearance of the fault (the effect)
 */
public class CauseEffectPruner implements Pruner, FeedbackHandler {

    private final Set<FaultUid> pointsInHappyPath = new HashSet<>();
    private final ConditionalStore redundancyStore = new ConditionalStore();
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
    }

    private void handleHappensBefore(Set<Fault> cause, FaultUid effect, FeedbackContext context) {
        logger.info("Found exclusion: " + cause + " hides " + effect);
        redundancyStore.addCondition(cause, effect);
        context.reportExclusionOfFaultUid(cause, effect);
    }

    @Override
    public PruneDecision prune(Faultload faultload) {
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
                .filter(redundancyStore::hasConditions)
                .collect(Collectors.toSet());

        boolean isRedundant = relatedUidsInFaultload.stream()
                // for any related fid's causes
                .anyMatch(f -> redundancyStore.hasCondition(faultset, f));

        if (isRedundant) {
            return PruneDecision.PRUNE_SUBTREE;
        } else {
            return PruneDecision.KEEP;
        }
    }

}
