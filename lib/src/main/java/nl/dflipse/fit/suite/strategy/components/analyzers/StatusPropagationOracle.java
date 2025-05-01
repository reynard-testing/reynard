package io.github.delanoflipse.fit.suite.strategy.components.analyzers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.util.Sets;
import io.github.delanoflipse.fit.suite.trace.tree.TraceReport;

public class StatusPropagationOracle implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(StatusPropagationOracle.class);

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

        // Are we seeing a status code that has a specific meaning?
        int statusCode = report.response.status;
        if (statusCode <= 500) {
            return;
        }

        var faultyChildren = result.trace.getChildren(report).stream()
                .filter(r -> r.hasFaultBehaviour())
                .toList();

        // Find a list of potential culprits
        var responsible = faultyChildren.stream()
                .filter(r -> r.response.status == statusCode)
                .map(r -> r.getBehaviour())
                .toList();

        if (responsible.isEmpty()) {
            if (faultyChildren.isEmpty()) {
                // Case 1: There is no upstream cause for this error
                // Which is a systems bug
                logger.warn("Found a fault of status code {} at {}, but no cause! This is highly likely a bug!",
                        statusCode,
                        report.faultUid);
            } else {
                // Case 2: The node is sending weird error status codes
                // Which is also not ideal
                var causes = faultyChildren.stream()
                        .map(r -> r.getBehaviour())
                        .toList();
                logger.warn("Found a fault of status code {} at {}, which is caused by any of {}", statusCode,
                        report.faultUid, causes);
                logger.warn(
                        "This error code with a specific meaning can cause incorrect behaviour at the upstream node!");
            }

            return;
        }

        String resposibleStr = null;
        if (responsible.size() == 1) {
            resposibleStr = String.valueOf(Sets.getOnlyElement(responsible));
        } else {
            resposibleStr = String.valueOf(responsible);
        }

        // Alert the user!
        logger.warn("Found a propogated fault of status code {} at {}, cause by: {}.", statusCode, report.faultUid,
                resposibleStr);
        logger.warn(
                "Forwarding error codes with specific meaning can cause incorrect behaviour at the upstream node!");
    }

}
