package nl.dflipse.fit.strategy.pruners;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.UndirectedRelation;

public class ConcurrencyPruner implements FeedbackHandler, Pruner {
    private final Map<FaultUid, Set<FaultUid>> concurrentFaults = new HashMap<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        for (var relation : result.trace.getAllConcurrent()) {
            for (var pair : Sets.pairs(relation)) {
                concurrentFaults.computeIfAbsent(pair.first(), k -> new HashSet<>()).add(pair.second());
                concurrentFaults.computeIfAbsent(pair.second(), k -> new HashSet<>()).add(pair.first());
            }
        }
    }

    @Override
    public boolean prune(Faultload faultload) {

    }

}
