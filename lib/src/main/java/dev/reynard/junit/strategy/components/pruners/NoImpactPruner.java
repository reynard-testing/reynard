package dev.reynard.junit.strategy.components.pruners;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.faultload.Faultload;
import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.components.FeedbackContext;
import dev.reynard.junit.strategy.components.FeedbackHandler;
import dev.reynard.junit.strategy.components.PruneContext;
import dev.reynard.junit.strategy.components.PruneDecision;
import dev.reynard.junit.strategy.components.Pruner;
import dev.reynard.junit.strategy.util.Sets;

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

        var points = context.getFaultInjectionPoints();

        for (Fault fault : injected) {
            FaultUid parent = result.trace.getParent(fault.uid());

            if (parent == null) {
                continue;
            }

            var report = result.trace.getReportByFaultUid(parent);
            if (report == null) {
                continue;
            }

            if (report.response.isErrenous()) {
                continue;
            }

            // Note: this quite hardwired to the logic of ConditionalFaultDetector
            boolean isCauseForRetry = points.contains(fault.uid().asAnyCount());
            if (isCauseForRetry) {
                continue;
            }

            // TODO: check if causes alternative paths
            // boolean isCauseForAlternativePath =
            // context.getStore().isAnyInclusionCause(fault.asBehaviour());

            // if (isCauseForAlternativePath) {
            // continue;
            // }

            // TODO: check if output of parent is the same regardless of the fault
            // To avoid capturing local fallbacks

            if (pruneImpactlessFaults) {
                logger.info("Detected impactless fault: " + fault);
                impactlessFaults.add(Set.of(fault));
                context.pruneFaultSubset(Set.of(fault));
            } else {
                // TODO: check for all combinations of neighbours?
                logger.info("Detected impactless fault?: " + fault);
            }
        }
    }

    @Override
    public PruneDecision prune(Faultload faultload, PruneContext context) {
        for (var impactless : impactlessFaults) {
            if (Sets.isSubsetOf(impactless, faultload.faultSet())) {
                return PruneDecision.PRUNE_SUPERSETS;
            }
        }

        return PruneDecision.KEEP;
    }

}
