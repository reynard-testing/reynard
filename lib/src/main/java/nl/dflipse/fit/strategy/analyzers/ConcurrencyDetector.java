package nl.dflipse.fit.strategy.analyzers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.Reporter;
import nl.dflipse.fit.strategy.util.UndirectedRelation;

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
    public Map<String, String> report() {
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
