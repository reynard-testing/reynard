package nl.dflipse.fit.strategy.analyzers;

import java.util.ArrayList;
import java.util.Collections;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;

public class RandomDetector implements FeedbackHandler<Void> {

    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            var potentialFaults = result.trace.getFaultUids(TraversalStrategy.DEPTH_FIRST);

            var shuffledFaults = new ArrayList<>(potentialFaults);
            Collections.shuffle(shuffledFaults);

            for (var fault : shuffledFaults) {
                System.out.println("[RG] Found fault: " + fault);
            }

            context.reportFaultUids(potentialFaults);
        }

        return null;
    }

}
