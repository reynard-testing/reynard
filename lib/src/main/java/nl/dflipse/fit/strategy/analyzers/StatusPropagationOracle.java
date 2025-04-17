package nl.dflipse.fit.strategy.analyzers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.trace.tree.TraceReport;

public class StatusPropagationOracle implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(StatusPropagationOracle.class);

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        
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
            if (statusCode == 500) {
                continue;
            }

            // Find a list of potential culprits
            var responsible = result.trace.getChildren(report).stream()
                .filter(r -> r.hasFaultBehaviour())
                .filter(r -> r.response.status == statusCode)
                .toList();
            
            // Alert the user!
            logger.warn("Found a propogated fault of status code {} at {}, likely cause by: {}.", statusCode, report.faultUid, responsible);
            logger.warn("Forwarding error codes with specific meaning can cause incorrect behaviour at the upstream node!");
        }
    }
    
}
