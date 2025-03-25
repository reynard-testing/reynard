package nl.dflipse.fit.strategy.analyzers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.Reporter;

public class StatusAnalyzer implements FeedbackHandler<Void>, Reporter {
    private final Map<String, Set<Integer>> faultStatus = new HashMap<>();

    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
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
        return null;
    }

    @Override
    public Map<String, String> report() {
        Map<String, String> report = new HashMap<>();
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
