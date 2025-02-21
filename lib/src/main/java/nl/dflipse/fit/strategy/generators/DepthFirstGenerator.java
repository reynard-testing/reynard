package nl.dflipse.fit.strategy.generators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.util.Combinatorics;
import nl.dflipse.fit.trace.util.TraceTraversal;

public class DepthFirstGenerator implements Generator {
    private boolean first = true;
    private List<FaultMode> modes;

    private Set<Fault> asFaults(List<FaultUid> ids, FaultMode mode) {
        Set<Fault> faults = new HashSet<>();

        for (var id : ids) {
            faults.add(new Fault(id, mode));
        }

        return faults;
    }

    public DepthFirstGenerator() {
        // DelayFault.fromDelayMs(1000),
        // ErrorFault.fromError(ErrorFault.HttpError.REQUEST_TIMEOUT)

        modes = List.of(
                ErrorFault.fromError(ErrorFault.HttpError.SERVICE_UNAVAILABLE));
    }

    @Override
    public List<Faultload> generate(FaultloadResult result) {
        if (!first) {
            return List.of();
        }

        first = false;

        var potentialFaults = TraceTraversal.depthFirstFaultpoints(result.trace);
        var powerSet = Combinatorics.generatePowerSet(potentialFaults);

        List<Faultload> newFaultloads = new ArrayList<>();
        for (var faultIds : powerSet) {
            if (faultIds.isEmpty()) {
                // powerset contains the empty set, ignore that one (we already covered it)
                continue;
            }

            for (var mode : modes) {
                var newFaultload = new Faultload(asFaults(faultIds, mode));
                newFaultloads.add(newFaultload);
            }
        }

        System.out.println("[DFS] Planning on testing " + newFaultloads.size() + " combinations!");
        return newFaultloads;
    }

}
