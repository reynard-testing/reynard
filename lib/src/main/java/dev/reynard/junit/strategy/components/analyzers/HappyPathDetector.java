package dev.reynard.junit.strategy.components.analyzers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.reynard.junit.instrumentation.trace.tree.TraceReport;
import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.components.FeedbackContext;
import dev.reynard.junit.strategy.components.FeedbackHandler;
import dev.reynard.junit.strategy.components.PruneContext;
import dev.reynard.junit.strategy.components.Reporter;

public class HappyPathDetector implements FeedbackHandler, Reporter {

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (!result.isInitial()) {
            // TODO: handle case where alternative paths are also considered happy paths
            // But, this has issues with the check for retries in the happy path
            return;
        }

        for (var report : result.trace.getReports()) {
            if (report.response.isErrenous()) {
                continue;
            }

            var happyPath = context.getHappyPath(report.injectionPoint);
            if (happyPath != null) {
                continue;
            }

            List<TraceReport> children = result.trace.getChildren(report);
            boolean isHappyPath = children.stream()
                    .allMatch(r -> !r.hasFaultBehaviour());

            if (isHappyPath) {
                context.reportHappyPath(report);
            }
        }
    }

    @Override
    public Object report(PruneContext context) {
        var happyPath = context.getHappyPaths();

        List<Object> report = new ArrayList<>();

        for (var entry : happyPath) {
            Map<String, Object> reportEntry = new LinkedHashMap<>();
            var response = entry.response;
            reportEntry.put("point", entry.injectionPoint.toString());
            String bodyLimited = response.body.replace("\n", "");
            if (response.body.length() > 200) {
                bodyLimited = response.body.substring(0, 97) + "...";
            }
            reportEntry.put("response", bodyLimited);
            reportEntry.put("status", response.status);
            reportEntry.put("durationMs", response.durationMs);
            report.add(reportEntry);
        }

        return report;
    }

}
