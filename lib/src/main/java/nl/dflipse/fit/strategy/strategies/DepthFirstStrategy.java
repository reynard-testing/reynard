package nl.dflipse.fit.strategy.strategies;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import nl.dflipse.fit.strategy.FIStrategy;
import nl.dflipse.fit.strategy.Fault;
import nl.dflipse.fit.strategy.Faultload;
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

    @Override
    public void handleResult(Faultload faultload, TraceData trace, boolean passed) {
        if (!passed) {
            failed = true;
            return;
        }

        boolean wasFirst = first;
        first = false;

        if (wasFirst) {
            var potentialFaults = TraceTraversal.depthFirstFaultpoints(trace);
            var powerSet = Combinatorics.generatePowerSet(potentialFaults);
            FaultMode httpFault = new ErrorFault(ErrorFault.HttpError.SERVICE_UNAVAILABLE);

            for (var faultIds : powerSet) {
                if (faultIds.isEmpty()) {
                    continue;
                }

                List<Fault> faults = new ArrayList<>();
                for (var faultId : faultIds) {
                    faults.add(new Fault(httpFault, faultId));
                }

                var newFaultload = new Faultload(faults);
                queue.add(newFaultload);
            }

            return;
        }
    }
}
