package dev.reynard.junit.strategy.components.analyzers;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.components.FeedbackContext;
import dev.reynard.junit.strategy.components.FeedbackHandler;
import dev.reynard.junit.strategy.util.traversal.TraversalOrder;

public class ParentChildDetector implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(ParentChildDetector.class);
    private final Set<FaultUid> knownPoints = new HashSet<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        // --- Report upstream causes and effects ---
        result.trace.traverseReports(TraversalOrder.BREADTH_FIRST, true, report -> {
            FaultUid cause = report.injectionPoint;
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

}
