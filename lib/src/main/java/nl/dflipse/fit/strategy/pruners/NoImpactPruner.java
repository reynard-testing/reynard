package nl.dflipse.fit.strategy.pruners;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;

public class NoImpactPruner implements Pruner, FeedbackHandler<Void> {
    private final Logger logger = LoggerFactory.getLogger(NoImpactPruner.class);
    private Set<Set<Fault>> impactlessFaults = new HashSet<>();

    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            return null;
        }

        Set<Fault> injected = result.trace.getInjectedFaults();
        if (injected.isEmpty()) {
            return null;
        }

        // TODO: include root response
        // TODO: verify, is this assumption correct?
        // Or only in the context of the parent event?
        // What if only as single fault this is okay?
        boolean hasImpact = result.trace.getReports().stream()
                .filter(r -> r.injectedFault == null || !injected.contains(r.injectedFault))
                .anyMatch(r -> r.response.isErrenous());

        if (hasImpact) {
            return null;
        }

        logger.info("Found impactless faultload: " + result.faultload);
        impactlessFaults.add(injected);
        context.pruneFaultSubset(injected);

        return null;
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
