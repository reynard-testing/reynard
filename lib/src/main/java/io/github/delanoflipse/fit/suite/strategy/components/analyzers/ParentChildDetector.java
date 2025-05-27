package io.github.delanoflipse.fit.suite.strategy.components.analyzers;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.util.TraversalStrategy;

public class ParentChildDetector implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(ParentChildDetector.class);
    private final Set<FaultUid> knownPoints = new HashSet<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
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

}
