package nl.dflipse.fit.strategy.store;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.util.Sets;

public class PreconditionStore {
    private final Map<FaultUid, Set<Set<Fault>>> store = new HashMap<>();

    public boolean hasPrecondition(Set<Fault> condition, FaultUid fid) {
        if (!store.containsKey(fid)) {
            return false;
        }

        for (var subset : store.get(fid)) {
            if (Sets.isSubsetOf(subset, condition)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasPreconditions(FaultUid fid) {
        return store.containsKey(fid);
    }

    public boolean addPrecondition(Set<Fault> condition, FaultUid fid) {
        if (hasPrecondition(condition, fid)) {
            return false;
        }

        store.computeIfAbsent(fid, x -> new HashSet<>());
        store.get(fid).removeIf(s -> Sets.isSubsetOf(condition, s));
        store.get(fid).add(condition);

        return true;
    }

    public Set<FaultUid> getForPrecondition(Set<Fault> condition) {
        Set<FaultUid> result = new HashSet<>();
        for (var entry : store.entrySet()) {
            var fid = entry.getKey();
            if (hasPrecondition(condition, fid)) {
                result.add(fid);
            }
        }
        return result;
    }

    public Map<FaultUid, Set<Set<Fault>>> getStore() {
        return store;
    }

}
