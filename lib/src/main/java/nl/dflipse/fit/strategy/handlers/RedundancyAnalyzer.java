package nl.dflipse.fit.strategy.handlers;

import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.HistoricStore;

public class RedundancyAnalyzer implements FeedbackHandler<Void> {

    @Override
    public Void handleFeedback(FaultloadResult result, HistoricStore history) {
        // assert that the faults were injected
        var intendedFaults = result.faultload.getFaults();
        if (intendedFaults.size() == 0) {
            return null;
        }

        var injectedFaults = result.trace.getFaults();
        var notInjectedFaults = result.getNotInjectedFaults();

        if (notInjectedFaults.size() == intendedFaults.size()) {
            System.out.println("No faults were injected!");
            System.out.println("There is a high likelyhood of the fault injection not working correctly!");
        } else if (!notInjectedFaults.isEmpty()) {
            System.out.println("Not all faults were injected, missing:" + notInjectedFaults);
            System.out.println("This can be due to redundant faults or a bug in the fault injection!");
        }

        return null;
    }
}
