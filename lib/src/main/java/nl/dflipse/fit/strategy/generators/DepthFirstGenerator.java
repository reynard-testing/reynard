package nl.dflipse.fit.strategy.generators;

import java.util.List;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;

public class DepthFirstGenerator extends IncreasingSizeGenerator implements FeedbackHandler<Void> {

    public DepthFirstGenerator(List<FaultMode> modes) {
        super(modes);
    }

    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            var potentialFaults = result.trace.getFaultUids(TraversalStrategy.DEPTH_FIRST);

            for (var fault : potentialFaults) {
                System.out.println("[DFS] Found fault: " + fault);
            }

            reportFaultUids(potentialFaults);
        }

        return null;
    }

}
