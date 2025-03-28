package nl.dflipse.fit.strategy.pruners;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;

public class ErrorPropogationPruner implements Pruner, FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(ErrorPropogationPruner.class);

    private List<Set<Fault>> redundantFautloads = new ArrayList<>();
    private List<Set<Fault>> redundantSubsets = new ArrayList<>();

    // TODO: if the body is the same as the error, and the status code,
    // Do we need to check for the other modes?
    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        Set<Fault> injectedFaults = result.trace.getInjectedFaults();

        for (var report : result.trace.getReports()) {
            if (report.isInitial || report.response == null) {
                continue;
            }

            boolean isErrenousResponse = report.response.isErrenous();
            boolean isInjectedError = report.injectedFault != null
                    && report.injectedFault.mode().getType().equals(ErrorFault.FAULT_TYPE);

            boolean isErrorPropogated = isErrenousResponse && !isInjectedError;
            if (!isErrorPropogated) {
                continue;
            }

            FaultMode faultMode = new FaultMode(ErrorFault.FAULT_TYPE, List.of("" + report.response.status));
            Fault responseFault = new Fault(report.faultUid, faultMode);

            handlePropogation(injectedFaults, responseFault, context);
        }

    }

    private void handlePropogation(Set<Fault> causes, Fault effect, FeedbackContext context) {
        logger.info("Found that fault(s) " + causes + " causes error " + effect);

        // We don't need to check for this exact fault, as it is already
        // been tested
        // Set<Fault> newRedundantFaultload = Set.of(effect);
        // redundantFautloads.add(newRedundantFaultload);
        // context.pruneFaultload(new Faultload(newRedundantFaultload));

        // We also don't need to check for the subset of this fault
        // and its causes
        Set<Fault> newRedundantSubset = Sets.plus(causes, effect);

        redundantSubsets.add(newRedundantSubset);
        context.pruneFaultSubset(newRedundantSubset);

        // TODO: if a cause is directly propogated -> cause & effect are redundant
        // TODO: use unique fault ids to correlate fault & behaviour.
    }

    @Override
    public boolean prune(Faultload faultload) {
        for (var redundantFaultload : redundantFautloads) {
            if (faultload.faultSet().equals(redundantFaultload)) {
                return true;
            }
        }

        for (var redundantSubset : redundantSubsets) {
            if (faultload.faultSet().containsAll(redundantSubset)) {
                return true;
            }
        }

        return false;
    }

}
