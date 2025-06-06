package io.github.delanoflipse.fit.suite.strategy.components.analyzers;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.components.PruneContext;
import io.github.delanoflipse.fit.suite.strategy.components.Reporter;

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
