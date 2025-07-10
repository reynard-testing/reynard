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

public class StatusPropagationOracle implements FeedbackHandler, Reporter {
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

        // Do we observe a fault we did not inject?
        boolean isIndirectError = report.hasIndirectFaultBehaviour();
        if (!isIndirectError) {
            return;
        }

        // Are we seeing a status code that has a specific meaning?
        int statusCode = report.response.status;
        if (statusCode <= 500) {
            return;
        }

        Fault fault = report.getFault();

        var faultyChildren = result.trace.getChildren(report).stream()
                .filter(r -> r.hasFaultBehaviour())
                .toList();

        // Find a list of potential culprits
        var responsible = faultyChildren.stream()
                .filter(r -> r.response.status == statusCode)
                .map(r -> r.getFault())
                .toList();

        // Either the server has sent a
        if (!responsible.isEmpty()) {
            observations.computeIfAbsent(fault, x -> new ArrayList<>())
                    .add(responsible);

            // Alert the user!
            logger.warn("Found a propogated fault of status code {} at {}, cause by: {}.", statusCode,
                    report.injectionPoint);
            logger.warn(
                    "Forwarding error codes with specific meaning can cause incorrect behaviour at the upstream node!");
            return;
        }

        // If there are no faulty children, then its a fault without cause
        // (other oracle)
        if (faultyChildren.isEmpty()) {
            return;
        }

        // Or: The node is sending incorrect error status codes by default
        // Which is also not ideal
        var causes = faultyChildren.stream()
                .map(r -> r.getFault())
                .toList();

        observations.computeIfAbsent(fault, x -> new ArrayList<>())
                .add(causes);

        // Alert the user!
        logger.warn("Found a fault of status code {} at {}, which is caused by any of {}", statusCode,
                report.injectionPoint, causes);
        logger.warn(
                "This error code with a specific meaning can cause incorrect behaviour at the upstream node!");

    }

    @Override
    public Object report(PruneContext context) {
        List<Map<String, Object>> report = new ArrayList<>();

        for (var entry : observations.entrySet()) {
            Fault fault = entry.getKey();

            Map<String, Object> bugReport = new LinkedHashMap<>();
            // Log observed fault wihtout causes
            bugReport.put("propagated_fault", Map.of(
                    "uid", fault.uid().toString(),
                    "mode", fault.mode().toString()));
            // Output all intended causes
            bugReport.put("causes", entry.getValue().stream()
                    .map(f -> f.stream()
                            .map(x -> x.toString()).toList()));
            report.add(bugReport);
        }

        return report;
    }

}
