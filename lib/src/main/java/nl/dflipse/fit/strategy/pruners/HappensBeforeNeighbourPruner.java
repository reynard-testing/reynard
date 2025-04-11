package nl.dflipse.fit.strategy.pruners;

import java.util.HashSet;
import java.util.List;
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
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;
import nl.dflipse.fit.trace.tree.TraceReport;

/**
 * Pruner that prunes faults that are redundant due to cause-effect
 * relationships
 * I.e. if a fault is injected, and another fault disappears,
 * s set of fault causes the disappearance of the fault (the effect)
 */
public class CauseEffectPruner implements Pruner, FeedbackHandler {

    private final ConditionalStore redundancyStore = new ConditionalStore();
    private final Logger logger = LoggerFactory.getLogger(CauseEffectPruner.class);

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            return;
        }

        Set<Fault> injectedErrorFaults = result.trace.getInjectedFaults();
        Set<FaultUid> expectedPoints = context.getStore().getExpectedPoints(injectedErrorFaults);

        result.trace.traverseReports(TraversalStrategy.BREADTH_FIRST, true, report -> {
            List<TraceReport> childrenReports = result.trace.getChildren(report);

            Set<FaultUid> expectedChildren = expectedPoints.stream()
                    .filter(f -> f.hasParent() && f.getParent().matches(report.faultUid))
                    .collect(Collectors.toSet());

            Set<FaultUid> observedChildren = childrenReports.stream()
                    .map(f -> f.faultUid)
                    .filter(f -> f != null)
                    .collect(Collectors.toSet());

            Set<FaultUid> dissappeared = missingPoints(expectedChildren, observedChildren);

            if (dissappeared.isEmpty()) {
                return;
            }

            // TODO: use only children and substitution
            // using representative faults!
            Set<Fault> causes = result.trace.getDecendants(report).stream()
                    .map(r -> r.injectedFault)
                    .filter(f -> f != null)
                    .collect(Collectors.toSet());

            for (FaultUid fault : dissappeared) {
                handleHappensBefore(causes, fault, context);
            }
        });
    }

    private Set<FaultUid> missingPoints(Set<FaultUid> expected, Set<FaultUid> observed) {
        Set<FaultUid> missing = new HashSet<>();

        for (FaultUid point : expected) {
            boolean found = false;

            for (FaultUid seen : observed) {
                if (seen.matches(point)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                missing.add(point);
            }
        }

        return missing;
    }

    private void handleHappensBefore(Set<Fault> cause, FaultUid effect, FeedbackContext context) {
        if (cause.isEmpty()) {
            logger.info("Found unknown cause that hides {}", effect);
            return;
        }
        logger.info("Found exclusion: {} hides {}", cause, effect);
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
                .anyMatch(f -> redundancyStore.hasCondition(f, faultset));

        if (isRedundant) {
            return PruneDecision.PRUNE_SUBTREE;
        } else {
            return PruneDecision.KEEP;
        }
    }

}
