package nl.dflipse.fit.strategy.analyzers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.trace.tree.TraceReport;

public class ErrorPropagationDetector implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(ErrorPropagationDetector.class);

    private Set<Fault> redundantFaults = new HashSet<>();

    // TODO: if the body is the same as the error, and the status code,
    // Do we need to check for the other modes?
    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        for (TraceReport report : result.trace.getReports()) {
            // Skip the initial report, we don't care about it
            if (report.isInitial || report.response == null) {
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
                logger.warn("Unexpected error {}: " + report.response.status);
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
        redundantFaults.add(effect);
        context.reportDownstreamEffect(causes, effect.asBehaviour());
    }
}
