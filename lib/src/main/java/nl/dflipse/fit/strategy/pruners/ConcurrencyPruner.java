package nl.dflipse.fit.strategy.pruners;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.UndirectedRelation;

public class ConcurrencyPruner implements FeedbackHandler, Pruner {
    private final UndirectedRelation<FaultUid> relation = new UndirectedRelation<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        for (var relation : result.trace.getAllConcurrent().entrySet()) {
            var a = relation.getKey();
            for (var b : relation.getValue()) {
                this.relation.addRelation(a, b);
            }
        }
    }

    @Override
    public boolean prune(Faultload faultload) {
        // Given a clique of concurrent faults
        // Either inject one fault, or all of them

        Map<FaultUid, Set<FaultUid>> getByUpToCount = faultload.faultSet().stream()
                .collect(Collectors.groupingBy(f -> f.uid().asAnyCount(),
                        Collectors.mapping(Fault::uid, Collectors.toSet())));

        // TODO
        Set<FaultUid> expectedPoints = Set.of();

        for (var entry : getByUpToCount.entrySet()) {
            var key = entry.getKey();
            var relatedPoints = expectedPoints.stream()
                    .filter(f -> f.matchesUpToCount(key))
                    .collect(Collectors.toSet());
            var relatedPointsInFaultload = entry.getValue();

            if (relation.isClique(relatedPoints)) {
                return true;
            }
        }
        return false;
    }

}
