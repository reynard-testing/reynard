package io.github.delanoflipse.fit.strategy.components.analyzers;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.delanoflipse.fit.strategy.FaultloadResult;
import io.github.delanoflipse.fit.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.strategy.components.PruneContext;
import io.github.delanoflipse.fit.strategy.components.Reporter;

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

            String downstream = report.faultUid.destination();
            int status = report.response.status;
            faultStatus.computeIfAbsent(downstream, k -> new LinkedHashSet<>()).add(status);
        }
    }

    @Override
    public Map<String, String> report(PruneContext context) {
        Map<String, String> report = new LinkedHashMap<>();
        for (var entry : faultStatus.entrySet()) {
            String downstream = entry.getKey();
            Set<Integer> statuses = entry.getValue();
            List<String> sortedStatuses = statuses.stream()
                    .sorted()
                    .map(String::valueOf)
                    .collect(Collectors.toList());
            String joinedStatuses = String.join(",", sortedStatuses);
            report.put(downstream, joinedStatuses);
        }
        return report;
    }
}
