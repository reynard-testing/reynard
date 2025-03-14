package nl.dflipse.fit.strategy.analyzers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.util.Sets;

public class RedundancyAnalyzer implements FeedbackHandler<Void> {
    private Set<FaultUid> detectedUids = new HashSet<>();
    private FaultloadResult initialResult;

    private Set<FaultUid> analyzeAppearedFaultUids(FaultloadResult result) {
        var presentFaultUids = result.trace.getFaultUids();
        var appearedFaultUids = Sets.difference(presentFaultUids, detectedUids);

        if (!appearedFaultUids.isEmpty()) {
            System.out.println("New fault points appeared: " + appearedFaultUids);
        }

        return appearedFaultUids;
    }

    private Set<Fault> analyzeDisappearedFaults(FaultloadResult result) {

        // get the intended faults in the faultload
        Set<Fault> intendedFaults = result.faultload.getFaultload().faultSet();
        Set<Fault> injectedFaults = result.trace.getInjectedFaults();
        Set<Fault> notInjectedFaults = result.getNotInjectedFaults();

        if (notInjectedFaults.size() == intendedFaults.size()) {
            System.out.println("No faults were injected!");
            System.out.println("There is a high likelyhood of the fault injection not working correctly!");
            System.out.println("Missing: " + intendedFaults);
        } else if (!notInjectedFaults.isEmpty()) {
            System.out.println("Not all faults were injected, missing:" + notInjectedFaults);
            System.out.println("This can be due to redundant faults or a bug in the fault injection!");
        }

        return notInjectedFaults;
    }

    private void detectRandomFaults(Set<FaultUid> appeared, Set<Fault> disappeared) {
        for (var fault : disappeared) {
            var faultWithoutPayload = fault.getUid().asAnyPayload();

            // find appeared faults that match the disappeared fault
            // up to the payload
            List<FaultUid> counterParts = appeared
                    .stream()
                    .filter(f -> f.matches(faultWithoutPayload))
                    .collect(Collectors.toList());

            if (counterParts.isEmpty()) {
                continue;
            }

            System.out.println(
                    "There is a high likelyhood that payloads contain nondeterministic values (either random or time-based)");

            if (counterParts.size() == 1) {
                System.out.println("Fault " + fault + " turned into " + counterParts.get(0));
                continue;
            }

            // if there are multiple appeared faults that match the disappeared fault
            System.out.println("Fault " + fault + " dissapeared, but multiple appeared faults match:");
            for (var appearedFault : counterParts) {
                System.out.println("Matches " + appearedFault);
            }
        }
    }

    @Override
    public Void handleFeedback(FaultloadResult result, FeedbackContext context) {
        if (result.isInitial()) {
            initialResult = result;
            detectedUids.addAll(initialResult.trace.getFaultUids());
            return null;
        }

        // ----------------- Analyse the fault injection -----------------
        // Analyse new paths that were not in the original trace
        var appeared = analyzeAppearedFaultUids(result);
        var disappeared = analyzeDisappearedFaults(result);
        detectRandomFaults(appeared, disappeared);

        // Report newly found points
        if (!appeared.isEmpty()) {
            context.reportFaultUids(List.copyOf(appeared));
            detectedUids.addAll(appeared);
        }

        return null;
    }
}
