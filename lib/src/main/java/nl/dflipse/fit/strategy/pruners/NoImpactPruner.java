package nl.dflipse.fit.strategy.pruners;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;

public class NoImpactPruner implements Pruner, FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(NoImpactPruner.class);
    private Set<Set<Fault>> impactlessFaults = new HashSet<>();
    private final boolean pruneImpactlessFaults;

    public NoImpactPruner(boolean pruneImpactlessFaults) {
        this.pruneImpactlessFaults = pruneImpactlessFaults;
    }

    public NoImpactPruner() {
        this(false);
    }

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            return;
        }

        Set<Fault> injected = result.trace.getInjectedFaults();
        if (injected.isEmpty()) {
            return;
        }

        for (Fault fault : injected) {
            FaultUid parent = result.trace.getParent(fault.uid());

            if (parent == null) {
                continue;
            }

            var report = result.trace.getReport(parent);
            if (report == null) {
                continue;
            }

            if (report.response.isErrenous()) {
                continue;
            }

            if (pruneImpactlessFaults) {
                logger.info("Detected impactless fault: " + fault);
                impactlessFaults.add(Set.of(fault));
                context.pruneFaultSubset(Set.of(fault));
            } else {
                // TODO: check for all combinations of neighbours?
                logger.info("Detected impactless fault?: " + fault);
            }
        }

        return;
    }

    @Override
    public boolean prune(Faultload faultload) {
        for (var impactless : impactlessFaults) {
            if (Sets.isSubsetOf(impactless, faultload.faultSet())) {
                return true;
            }
        }

        return false;
    }

}
