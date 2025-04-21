package nl.dflipse.fit.strategy.components.analyzers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.components.FeedbackContext;
import nl.dflipse.fit.strategy.components.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.trace.tree.TraceReport;

public class StatusPropagationOracle implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(StatusPropagationOracle.class);

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            return;
        }

        for (TraceReport report : result.trace.getReports()) {
            if (report.response == null) {
                continue;
            }

            // Are we reporting a fault, that we did not inject?
            boolean isIndirectError = report.hasIndirectFaultBehaviour();
            if (!isIndirectError) {
                continue;
            }

            // Are we seeing a status code that has a specific meaning?
            int statusCode = report.response.status;
            if (statusCode <= 500) {
                continue;
            }

            // Find a list of potential culprits
            var responsible = result.trace.getChildren(report).stream()
                    .filter(r -> r.hasFaultBehaviour())
                    .filter(r -> r.response.status == statusCode)
                    .map(r -> r.getBehaviour())
                    .toList();

            if (responsible.isEmpty()) {
                // No cause? that is not ideal...
                // Alert the user!
                logger.warn("Found a fault of status code {} at {}, WITHOUT ANY CAUSE!", statusCode, report.faultUid,
                        responsible);
                logger.warn("This is highly likely to be a bug!");
                continue;
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

}
