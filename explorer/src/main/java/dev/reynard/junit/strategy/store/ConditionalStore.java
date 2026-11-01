package dev.reynard.junit.strategy.store;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultUid;

public class ConditionalStore {
    private final Map<FaultUid, SubsetStore<Fault>> conditionsByUid = new HashMap<>();

    public boolean hasCondition(FaultUid fid, Set<Fault> condition) {

        if (!conditionsByUid.containsKey(fid)) {
            return false;
        }

        return conditionsByUid.get(fid).hasSubsetOf(condition);
    }

    public boolean hasConditions(FaultUid fid) {
        return conditionsByUid.containsKey(fid);
    }

    public boolean addCondition(Set<Fault> condition, FaultUid fid) {
        conditionsByUid.computeIfAbsent(fid, x -> new SubsetStore<>());
        conditionsByUid.get(fid).add(condition);

        return true;
    }

    public Set<FaultUid> getForCondition(Set<Fault> condition) {
        Set<FaultUid> result = new HashSet<>();
        for (var entry : conditionsByUid.entrySet()) {
            var fid = entry.getKey();
            if (entry.getValue().hasSubsetOf(condition)) {
                result.add(fid);
            }
        }
        return result;
    }

    public boolean isPartOfAnyCondition(Set<Fault> condition) {
        for (var subsetStore : conditionsByUid.values()) {
            if (subsetStore.hasSupersetOf(condition)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasForCondition(Set<Fault> condition) {
        for (var subsetStore : conditionsByUid.values()) {
            if (subsetStore.hasSubsetOf(condition)) {
                return true;
            }
        }
        return false;
    }

    public Map<FaultUid, SubsetStore<Fault>> getConditionsByUid() {
        return conditionsByUid;
    }

    public Map<FaultUid, List<Set<Fault>>> getConditionsByUidSets() {
        return conditionsByUid.entrySet().stream()
                .collect(HashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue().getSets()),
                        HashMap::putAll);
    }

}
