package nl.dflipse.fit.strategy.components.analyzers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.components.FeedbackContext;
import nl.dflipse.fit.strategy.components.FeedbackHandler;
import nl.dflipse.fit.strategy.components.Reporter;

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
            faultStatus.computeIfAbsent(downstream, k -> new HashSet<>()).add(status);
        }
    }

    @Override
    public Map<String, String> report() {
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
