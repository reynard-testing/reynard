package io.github.delanoflipse.fit.strategy.components.analyzers;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.faultload.Behaviour;
import io.github.delanoflipse.fit.faultload.Fault;
import io.github.delanoflipse.fit.strategy.FaultloadResult;
import io.github.delanoflipse.fit.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.trace.tree.TraceReport;

public class ErrorPropagationDetector implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(ErrorPropagationDetector.class);

    // TODO: if the body is the same as the error, and the status code,
    // Do we need to check for the other modes?
    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        for (TraceReport report : result.trace.getReports()) {
            // Skip the initial report, we don't care about it
            if (report.response == null) {
                continue;
            }

            // Are we reporting a fault, that we did not inject?
            boolean isIndirectError = report.hasIndirectFaultBehaviour();
            if (!isIndirectError) {
                continue;
            }

            // Find the direct causes that can influence the response
            List<TraceReport> childrenReports = result.trace.getChildren(report);
            // Get the direct and indirect causes
            Set<Behaviour> childCauses = childrenReports.stream()
                    .map(r -> r.getBehaviour())
                    .collect(Collectors.toSet());

            boolean isUnexpectedError = childCauses.isEmpty();
            if (isUnexpectedError) {
                logger.warn("Unexpected response without cause! {}", report.response);
                logger.warn("There is a high likelyhood that this is caused by some bug!");
                continue;
            }

            boolean isErrorPropogated = !childCauses.isEmpty();
            if (!isErrorPropogated) {
                continue;
            }

            Fault responseFault = report.getFault();
            handlePropogation(childCauses, responseFault, context);
        }
    }

    /* Causes result in fault at effect */
    private void handlePropogation(Set<Behaviour> causes, Fault effect, FeedbackContext context) {
        logger.info("Found that {} causes error {}", causes, effect);

        // TODO: if a cause is directly propogated (body, status)
        // Can we prune the faultUid?

        // TODO: what can we prune?
        // We can always test the cause, not the effect?
        // Its more of a substitution, if someting counts for the effect
        // The same is true for the cause
        context.reportDownstreamEffect(causes, effect.asBehaviour());
    }
}
