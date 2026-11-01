package dev.reynard.junit.strategy.components.analyzers;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.components.FeedbackContext;
import dev.reynard.junit.strategy.components.FeedbackHandler;
import dev.reynard.junit.strategy.components.PruneContext;
import dev.reynard.junit.strategy.components.Reporter;

public class StatusAnalyzer implements FeedbackHandler, Reporter {
    private final Map<String, Set<Integer>> faultStatus = new HashMap<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        for (var report : result.trace.getReports()) {
            if (report.response == null) {
                continue;
            }

            if (report.injectedFault != null) {
                continue;
            }

            String downstream = report.injectionPoint.destination();
            int status = report.response.status;
            faultStatus.computeIfAbsent(downstream, k -> new LinkedHashSet<>()).add(status);
        }
    }

    @Override
    public Object report(PruneContext context) {
        Map<String, Object> report = new LinkedHashMap<>();
        for (var entry : faultStatus.entrySet()) {
            String downstream = entry.getKey();
            Set<Integer> statuses = entry.getValue();
            List<Integer> sortedStatuses = statuses.stream()
                    .sorted()
                    .toList();
            report.put(downstream, sortedStatuses);
        }
        return report;
    }
}
