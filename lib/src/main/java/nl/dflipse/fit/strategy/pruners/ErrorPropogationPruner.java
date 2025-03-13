package nl.dflipse.fit.strategy.pruners;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;

public class ErrorPropogationPruner implements Pruner, FeedbackHandler<Void> {
    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        Set<Fault> injectedFaults = result.trace.getInjectedFaults();

        for (var report : result.trace.getReports()) {
            if (report.response == null) {
                continue;
            }

            boolean isErrenousResponse = report.response.isErrenous();
            boolean isInjectedError = report.injectedFault != null
                    && report.injectedFault.getMode().getType().equals(ErrorFault.FAULT_TYPE);

            if (isErrenousResponse && !isInjectedError) {
                FaultMode faultMode = new FaultMode(ErrorFault.FAULT_TYPE, List.of("" + report.response.status));
                Fault responseFault = new Fault(report.faultUid, faultMode);

                System.out.println(
                        "[ErrorPropagation] Found that faults " + injectedFaults + " causes error " + responseFault);

                // We don't need to check for this exact fault, as it is already
                // been tested
                context.ignoreFaultload(new Faultload(Set.of(responseFault)));

                // We also don't need to check for the subset of this fault
                // and its causes
                Set<Fault> newRedundantSubset = new HashSet<>(injectedFaults);
                newRedundantSubset.add(responseFault);

                context.ignoreFaultSubset(newRedundantSubset);
            }
        }

        return null;
    }

    @Override
    public boolean prune(Faultload faultload) {
        return false;
    }

}
