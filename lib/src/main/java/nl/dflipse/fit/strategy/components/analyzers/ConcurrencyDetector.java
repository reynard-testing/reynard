package io.github.delanoflipse.fit.suite.strategy.components.analyzers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.components.PruneContext;
import io.github.delanoflipse.fit.suite.strategy.components.Reporter;
import io.github.delanoflipse.fit.suite.strategy.util.UndirectedRelation;

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
    public Map<String, String> report(PruneContext context) {
        Map<String, String> report = new LinkedHashMap<>();

        for (var relation : relation.getRelations().entrySet()) {
            var f = relation.getKey();
            String value = relation.getValue().stream()
                    .map(FaultUid::toString)
                    .collect(Collectors.joining("\n"));
            report.put(f.toString(), value);
        }

        return report;
    }

}
