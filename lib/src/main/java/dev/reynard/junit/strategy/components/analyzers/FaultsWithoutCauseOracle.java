package dev.reynard.junit.strategy.components.analyzers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.instrumentation.trace.tree.TraceReport;
import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.components.FeedbackContext;
import dev.reynard.junit.strategy.components.FeedbackHandler;
import dev.reynard.junit.strategy.components.PruneContext;
import dev.reynard.junit.strategy.components.Reporter;

public class FaultsWithoutCauseOracle implements FeedbackHandler, Reporter {
    private final Logger logger = LoggerFactory.getLogger(StatusPropagationOracle.class);

    private final Map<Fault, List<List<Fault>>> observations = new LinkedHashMap<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            return;
        }

        for (TraceReport report : result.trace.getReports()) {
            handleReport(report, result);
        }
    }

    private void handleReport(TraceReport report, FaultloadResult result) {
        if (report.response == null) {
            return;
        }

        // Are we reporting a fault, that we did not inject?
        boolean isIndirectError = report.hasIndirectFaultBehaviour();
        if (!isIndirectError) {
            return;
        }

        List<TraceReport> descendants = result.trace.getChildren(report);

        boolean hasCause = descendants.stream().anyMatch(x -> x.hasFaultBehaviour());

        if (hasCause) {
            return;
        }

        Fault fault = report.getFault();
        observations.computeIfAbsent(fault, x -> new ArrayList<>())
                .add(result.trace.getInjectedFaults().stream().toList());
        logger.warn("Detected failure {} with no cause! This can be indicative of a bug!",
                fault);
    }

    @Override
    public Object report(PruneContext context) {
        List<Map<String, Object>> report = new ArrayList<>();

        for (var entry : observations.entrySet()) {
            Fault fault = entry.getKey();

            Map<String, Object> bugReport = new LinkedHashMap<>();
            // Log observed fault wihtout causes
            bugReport.put("observed_fault", fault.asReport());
            // Output all inteded causes
            bugReport.put("faultload", entry.getValue().stream()
                    .map(f -> f.stream().map(x -> x.asReport()).toList()));
            report.add(bugReport);
        }

        return report;
    }

}
