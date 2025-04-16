package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.modes.FailureMode;

public class Simplify {

    public static Pair<List<Set<Fault>>, List<Set<FaultUid>>> simplify(List<Set<Fault>> sets,
            Collection<FailureMode> failureModes) {
        List<Set<Fault>> faultSets = new ArrayList<>();
        List<Set<FaultUid>> faultUidSets = new ArrayList<>();

        Set<Integer> toSkip = new HashSet<>();

        for (int i = 0; i < sets.size(); i++) {
            if (toSkip.contains(i)) {
                continue;
            }

            var subset = sets.get(i);
            Set<FaultUid> faultUids = Faultload.getFaultUids(subset);

            Map<FaultUid, Set<FailureMode>> represented = new HashMap<>();
            for (var uid : faultUids) {
                represented.put(uid, new HashSet<>());
            }
            Set<Integer> skipIfFound = new HashSet<>();

            // if for every element in the subset,
            // all faults of all failure modes are present
            for (int j = i; j < sets.size(); j++) {
                if (toSkip.contains(j)) {
                    continue;
                }
                var other = sets.get(j);
                Set<FaultUid> otherUids = Faultload.getFaultUids(other);

                if (!otherUids.equals(faultUids)) {
                    continue;
                }

                skipIfFound.add(j);
                for (var fault : other) {
                    represented.get(fault.uid()).add(fault.mode());
                }
            }

            boolean allRepresented = true;
            for (var entry : represented.entrySet()) {
                var modes = entry.getValue();

                if (modes.size() != failureModes.size()) {
                    allRepresented = false;
                    break;
                }
            }

            if (allRepresented) {
                faultUidSets.add(faultUids);
                toSkip.addAll(skipIfFound);
            } else {
                faultSets.add(subset);
            }
        }

        return Pair.of(faultSets, faultUidSets);
    }
}
