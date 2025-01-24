package nl.dflipse.fit.strategy.strategies;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.strategy.FIStrategy;
import nl.dflipse.fit.strategy.Fault;
import nl.dflipse.fit.strategy.Faultload;
import nl.dflipse.fit.strategy.faultmodes.DelayFault;
import nl.dflipse.fit.strategy.faultmodes.ErrorFault;
import nl.dflipse.fit.strategy.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.strategies.util.Combinatorics;
import nl.dflipse.fit.strategy.strategies.util.TraceTraversal;
import nl.dflipse.fit.trace.data.TraceData;

public class DepthFirstStrategy implements FIStrategy {
    private boolean failed = false;
    private boolean first = true;
    private final Queue<Faultload> queue = new LinkedList<>();

    public DepthFirstStrategy() {
        // start out with an empty queue
        queue.add(new Faultload());
    }

    @Override
    public Faultload next() {
        if (failed) {
            return null;
        }
        return queue.poll();
    }

    private List<Fault> asFaults(List<String> ids, FaultMode mode) {
        List<Fault> faults = new ArrayList<>();

        for (var id : ids) {
            faults.add(new Fault(mode, id));
        }

        return faults;
    }

    @Override
    public void handleResult(Faultload faultload, TraceData trace, boolean passed) {
        if (!passed) {
            failed = true;
            return;
        }

        boolean wasFirst = first;
        first = false;

        // based on the analysis of the faultless trace
        // we can determine the potential faultpoints
        if (wasFirst) {
            var potentialFaults = TraceTraversal.depthFirstFaultpoints(trace);
            var powerSet = Combinatorics.generatePowerSet(potentialFaults);
            List<FaultMode> modes = List.of(
                    new ErrorFault(ErrorFault.HttpError.SERVICE_UNAVAILABLE),
                    new DelayFault(1000));

            for (var faultIds : powerSet) {
                if (faultIds.isEmpty()) {
                    continue;
                }

                for (var mode : modes) {
                    var newFaultload = new Faultload(asFaults(faultIds, mode));
                    queue.add(newFaultload);
                }
            }

            return;
        }

        // assert that the faults were injected
        Set<String> injectedFaults = faultload.getFaultload().stream()
                .map(f -> f.spanId)
                .collect(Collectors.toSet());
        int originalSize = faultload.size();

        if (originalSize == 0) {
            return;
        }

        TraceTraversal.traverseTrace(trace, span -> {
            if (span.report == null) {
                return;
            }

            if (injectedFaults.contains(span.report.spanUid) && span.report.faultInjected) {
                injectedFaults.remove(span.report.spanUid);
            }
        });

        if (injectedFaults.size() == originalSize) {
            System.out.println("No faults were injected!");
            System.out.println("There is a high likelyhood of the fault injection not working correctly!");
        }

        if (!injectedFaults.isEmpty()) {
            System.out.println("Not all faults were injected, missing:" + injectedFaults);
            System.out.println("This can be due to redundant faults or a bug in the fault injection!");
        }
    }
}
