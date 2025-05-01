package io.github.delanoflipse.fit.suite.strategy.components.analyzers;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.util.Sets;

public class RedundancyAnalyzer implements FeedbackHandler {
    private final Set<FaultUid> detectedUids = new LinkedHashSet<>();
    private FaultloadResult initialResult;
    private final Logger logger = LoggerFactory.getLogger(RedundancyAnalyzer.class);

    private Set<FaultUid> analyzeAppearedFaultUids(FaultloadResult result) {
        var presentFaultUids = result.trace.getFaultUids();
        var appearedFaultUids = Sets.difference(presentFaultUids, detectedUids);
        return appearedFaultUids;
    }

    private Set<Fault> analyzeDisappearedFaults(FaultloadResult result) {

        // get the intended faults in the faultload
        Set<Fault> intendedFaults = result.trackedFaultload.getFaultload().faultSet();
        Set<Fault> notInjectedFaults = result.getNotInjectedFaults();

        if (notInjectedFaults.size() == intendedFaults.size()) {
            logger.info("No faults were injected!");
            logger.info("There is a high likelyhood of the fault injection not working correctly!");
            logger.info("Missing: " + intendedFaults);
        } else if (!notInjectedFaults.isEmpty()) {
            logger.info("Not all faults were injected, missing:" + notInjectedFaults);
            logger.info("This can be due to redundant faults or a bug in the fault injection!");
        }

        return notInjectedFaults;
    }

    private void detectRandomFaults(Set<FaultUid> appeared, Set<Fault> disappeared) {
        for (var fault : disappeared) {
            var faultWithoutPayload = fault.uid().asAnyPayload();

            // find appeared faults that match the disappeared fault
            // up to the payload
            List<FaultUid> counterParts = appeared
                    .stream()
                    .filter(f -> f.matches(faultWithoutPayload))
                    .collect(Collectors.toList());

            if (counterParts.isEmpty()) {
                continue;
            }

            logger.info(
                    "There is a high likelyhood that payloads contain nondeterministic values (either random or time-based)");

            if (counterParts.size() == 1) {
                logger.info("Fault " + fault + " turned into " + counterParts.get(0));
                continue;
            }

            // if there are multiple appeared faults that match the disappeared fault
            logger.info("Fault " + fault + " dissapeared, but multiple appeared faults match:");
            for (var appearedFault : counterParts) {
                logger.info("Matches " + appearedFault);
            }
        }
    }

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            initialResult = result;
            detectedUids.addAll(initialResult.trace.getFaultUids());
            return;
        }

        // ----------------- Analyse the fault injection -----------------
        // Analyse new paths that were not in the original trace
        var appeared = analyzeAppearedFaultUids(result);
        var disappeared = analyzeDisappearedFaults(result);
        detectRandomFaults(appeared, disappeared);

        return;
    }
}
