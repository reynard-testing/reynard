package nl.dflipse.fit.strategy.analyzers;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.pruners.PruneDecision;
import nl.dflipse.fit.strategy.pruners.Pruner;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;
import nl.dflipse.fit.strategy.util.TransativeRelation;

public class ParentChildDetector implements Pruner, FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(ParentChildDetector.class);
    private TransativeRelation<FaultUid> happensBefore = new TransativeRelation<>();
    private final Set<FaultUid> knownPoints = new HashSet<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        for (var pair : result.trace.getParentsAndChildren()) {
            var parent = pair.first();
            var child = pair.second();
            happensBefore.addRelation(parent, child);
        }

        // --- Report upstream causes and effects ---
        result.trace.traverseReports(TraversalStrategy.BREADTH_FIRST, true, report -> {
            FaultUid cause = report.faultUid;
            if (knownPoints.contains(cause)) {
                return;
            }

            // Find all the effects of this point
            Set<FaultUid> effects = result.trace.getChildren(cause);
            if (effects.isEmpty()) {
                return;
            }

            logger.info("{} is the parent of:", cause);
            for (var effect : effects) {
                logger.info("\t--> {}", effect);
            }
            knownPoints.add(cause);
            context.reportUpstreamEffect(cause, effects);
        });
    }

    @Override
    public PruneDecision prune(Faultload faultload) {
        // if an http error is injected, and its children are also injected, it is
        // redundant.

        Set<FaultUid> errorFaults = faultload.getFaultUids();

        boolean isRedundant = Sets.anyPair(errorFaults, (pair) -> {
            var f1 = pair.first();
            var f2 = pair.second();
            return happensBefore.areRelated(f1, f2);
        });

        if (isRedundant) {
            return PruneDecision.PRUNE_SUBTREE;
        } else {
            return PruneDecision.KEEP;
        }
    }

}
