package nl.dflipse.fit.strategy.generators;

import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;

/*
 * Generate all possible combinations of faults in a breadth-first manner.
 * 
 */
public class BreadthFirstDetector implements FeedbackHandler<Void> {

    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            var potentialFaults = result.trace.getFaultUids(TraversalStrategy.BREADTH_FIRST);

            for (var fault : potentialFaults) {
                System.out.println("[BFS] Found fault: " + fault);
            }

            context.reportFaultUids(potentialFaults);

        }

        return null;
    }

}
