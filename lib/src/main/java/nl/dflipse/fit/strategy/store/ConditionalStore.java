package nl.dflipse.fit.strategy.store;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.util.Sets;

public class ConditionalStore {
    private final Map<FaultUid, Set<Set<Fault>>> store = new HashMap<>();

    public boolean hasCondition(Set<Fault> condition, FaultUid fid) {
        return hasCondition(store, condition, fid);
    }

    public static boolean hasCondition(Map<FaultUid, Set<Set<Fault>>> store, Set<Fault> condition, FaultUid fid) {
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

    public boolean hasConditions(FaultUid fid) {
        return store.containsKey(fid);
    }

    public boolean addCondition(Set<Fault> condition, FaultUid fid) {
        if (hasCondition(condition, fid)) {
            return false;
        }

        store.computeIfAbsent(fid, x -> new HashSet<>());
        store.get(fid).removeIf(s -> Sets.isSubsetOf(condition, s));
        store.get(fid).add(condition);

        return true;
    }

    public Set<FaultUid> getForCondition(Set<Fault> condition) {
        Set<FaultUid> result = new HashSet<>();
        for (var entry : store.entrySet()) {
            var fid = entry.getKey();
            if (hasCondition(condition, fid)) {
                result.add(fid);
            }
        }
        return result;
    }

    public static boolean hasForCondition(Map<FaultUid, Set<Set<Fault>>> store, Set<Fault> condition) {
        for (var entry : store.entrySet()) {
            var fid = entry.getKey();
            if (hasCondition(store, condition, fid)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPartOfAnyCondition(Map<FaultUid, Set<Set<Fault>>> store, Set<Fault> condition) {
        for (var entry : store.entrySet()) {
            for (var subset : entry.getValue()) {
                if (Sets.isSubsetOf(condition, subset)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasForCondition(Set<Fault> condition) {
        return hasForCondition(store, condition);
    }

    public Map<FaultUid, Set<Set<Fault>>> getStore() {
        return store;
    }

}
