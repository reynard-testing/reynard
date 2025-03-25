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

public class NoImpactPruner implements Pruner, FeedbackHandler<Void> {
    private Set<FaultUid> happyPath = new HashSet<>();
    private final Logger logger = LoggerFactory.getLogger(NoImpactPruner.class);
    private Set<Set<Fault>> impactlessFaults = new HashSet<>();

    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            happyPath = result.trace.getFaultUids();
            return null;
        }

        Set<Fault> injected = result.trace.getInjectedFaults();
        if (injected.isEmpty()) {
            return null;
        }
        // TODO: handle no impact in alternative paths

        // If the faultload creates alternative paths
        // TODO: refine, alternative path in parent!
        Set<FaultUid> fids = result.trace.getFaultUids();
        boolean alternativePath = Sets.isProperSupersetOf(fids, happyPath);
        if (alternativePath) {
            return null;
        }

        // TODO: verify, is this assumption correct?
        // Or only in the context of the parent event?
        // What if only as single fault this is okay?
        boolean hasImpact = result.trace.getReports().stream()
                .filter(r -> r.injectedFault == null || !injected.contains(r.injectedFault))
                .anyMatch(r -> r.response.isErrenous());

        if (hasImpact) {
            return null;
        }

        logger.info("Found impactless faultload: " + injected);
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
