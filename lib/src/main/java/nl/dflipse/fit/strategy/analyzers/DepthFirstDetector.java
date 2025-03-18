package nl.dflipse.fit.strategy.analyzers;

import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;

public class DepthFirstDetector implements FeedbackHandler<Void> {

    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            var potentialFaults = result.trace.getFaultUids(TraversalStrategy.DEPTH_FIRST);

            for (var fault : potentialFaults) {
                System.out.println("[DFS] Found fault: " + fault);
            }

            context.reportFaultUids(potentialFaults);
        }

        return null;
    }

}
