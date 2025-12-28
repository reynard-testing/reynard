package dev.reynard.junit.strategy.components.analyzers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.components.FeedbackContext;
import dev.reynard.junit.strategy.components.FeedbackHandler;
import dev.reynard.junit.strategy.components.PruneContext;
import dev.reynard.junit.strategy.components.Reporter;
import dev.reynard.junit.strategy.util.UndirectedRelation;

public class ConcurrencyDetector implements FeedbackHandler, Reporter {
    UndirectedRelation<FaultUid> relation = new UndirectedRelation<>();

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
    public Object report(PruneContext context) {
        Map<String, Object> report = new LinkedHashMap<>();

        for (var relation : relation.getRelations().entrySet()) {
            var f = relation.getKey();
            List<String> values = relation.getValue().stream()
                    .map(FaultUid::toString)
                    .collect(Collectors.toList());
            report.put(f.toString(), values);
        }

        return report;
    }

}
