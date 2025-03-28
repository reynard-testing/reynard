package nl.dflipse.fit.strategy.analyzers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.Reporter;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.UndirectedRelation;

public class ConcurrencyDetector implements FeedbackHandler, Reporter {
    UndirectedRelation<FaultUid> relation = new UndirectedRelation<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        for (var relation : result.trace.getAllConcurrent()) {
            for (var pair : Sets.pairs(relation)) {
                this.relation.addRelation(pair.first(), pair.second());
            }
        }
    }

    @Override
    public Map<String, String> report() {
        Map<String, String> report = new LinkedHashMap<>();

        int i = 0;
        for (var bag : relation.getRelations()) {
            String value = bag.stream()
                    .map(FaultUid::toString)
                    .collect(Collectors.joining("\n"));
            report.put("Group(" + i + ")", value);
        }

        return report;
    }

}
