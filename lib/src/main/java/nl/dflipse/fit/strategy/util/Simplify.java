package io.github.delanoflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.delanoflipse.fit.faultload.Behaviour;
import io.github.delanoflipse.fit.faultload.Fault;
import io.github.delanoflipse.fit.faultload.FaultUid;
import io.github.delanoflipse.fit.faultload.modes.FailureMode;

public class Simplify {

    public static Pair<List<Set<Fault>>, List<Set<FaultUid>>> simplify(List<Set<Fault>> sets,
            Collection<FailureMode> failureModes) {
        // Convert Fault sets to Behaviour sets
        List<Set<Behaviour>> behaviourSets = sets.stream()
                .map(x -> Behaviour.of(x))
                .toList();

        // Simplify Behaviour sets
        var simplified = simplifyBehaviour(behaviourSets, failureModes);

        // Convert back to Fault sets
        List<Set<Fault>> faultSets = simplified.first().stream()
                .map(x -> x.stream()
                        .map(Behaviour::getFault)
                        .collect(Collectors.toSet()))
                .toList();

        return Pair.of(faultSets, simplified.second());
    }

    public static Pair<List<Set<Behaviour>>, List<Set<FaultUid>>> simplifyBehaviour(List<Set<Behaviour>> sets,
            Collection<FailureMode> failureModes) {
        List<Set<Behaviour>> faultSets = new ArrayList<>();
        List<Set<FaultUid>> faultUidSets = new ArrayList<>();

        Set<Integer> toSkip = new LinkedHashSet<>();

        for (int i = 0; i < sets.size(); i++) {
            if (toSkip.contains(i)) {
                continue;
            }

            var subset = sets.get(i);
            Set<FaultUid> faultUids = Behaviour.getFaultUids(subset);

            Map<FaultUid, Set<FailureMode>> represented = new HashMap<>();
            for (var uid : faultUids) {
                represented.put(uid, new LinkedHashSet<>());
            }
            Set<Integer> skipIfFound = new LinkedHashSet<>();

            // if for every element in the subset,
            // all faults of all failure modes are present
            for (int j = i; j < sets.size(); j++) {
                if (toSkip.contains(j)) {
                    continue;
                }
                var other = sets.get(j);
                Set<FaultUid> otherUids = Behaviour.getFaultUids(other);

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
