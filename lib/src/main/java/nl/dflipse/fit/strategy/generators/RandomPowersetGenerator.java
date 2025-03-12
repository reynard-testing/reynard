package nl.dflipse.fit.strategy.generators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.PowersetIterator;

import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;

public class RandomPowersetGenerator extends IncreasingSizeGenerator implements FeedbackHandler<Void> {

    public RandomPowersetGenerator(List<FaultMode> modes) {
        super(modes);
    }

    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            var potentialFaults = result.trace.getFaultUids(TraversalStrategy.DEPTH_FIRST);

            var shuffledFaults = new ArrayList<>(potentialFaults);
            Collections.shuffle(shuffledFaults);

            for (var fault : shuffledFaults) {
                System.out.println("[RG] Found fault: " + fault);
            }

            reportFaultUids(potentialFaults);
        }

        return null;
    }

}
