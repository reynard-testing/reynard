package nl.dflipse.fit.strategy.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.faultmodes.FailureMode;

public class Faults {
    public static Set<Fault> getFaults(FaultUid point, Collection<FailureMode> modes) {
        return modes.stream()
                .map(mode -> new Fault(point, mode))
                .collect(Collectors.toSet());
    }

    public static Set<Set<Fault>> allFaults(Collection<FaultUid> point, Collection<FailureMode> modes) {
        return allFaults(List.copyOf(point), Set.of(), modes);
    }

    private static Set<Set<Fault>> allFaults(List<FaultUid> uids, Set<Fault> current, Collection<FailureMode> modes) {
        if (uids.isEmpty()) {
            return Set.of(current);
        }

        var head = uids.get(0);
        var tail = uids.subList(1, uids.size());
        Set<Fault> additions = getFaults(head, modes);
        Set<Set<Fault>> allSets = new HashSet<>();
        for (var addition : additions) {
            Set<Fault> newCurrent = Sets.plus(current, addition);
            allSets.addAll(allFaults(tail, newCurrent, modes));
        }

        return allSets;
    }
}
