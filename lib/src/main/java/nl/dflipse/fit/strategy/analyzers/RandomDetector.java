package nl.dflipse.fit.strategy.analyzers;

import java.util.ArrayList;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;

public class RandomDetector implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(RandomDetector.class);

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            var potentialFaults = result.trace.getFaultUids(TraversalStrategy.DEPTH_FIRST);

            var shuffledFaults = new ArrayList<>(potentialFaults);
            Collections.shuffle(shuffledFaults);

            for (var fault : shuffledFaults) {
                logger.info("Found fault: " + fault);
            }

            context.reportFaultUids(potentialFaults);
        }
    }

}
