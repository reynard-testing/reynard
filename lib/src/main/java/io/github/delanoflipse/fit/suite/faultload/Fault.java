package io.github.delanoflipse.fit.suite.faultload;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.github.delanoflipse.fit.suite.faultload.modes.FailureMode;
import io.github.delanoflipse.fit.suite.strategy.util.Sets;

@JsonSerialize
@JsonDeserialize
public record Fault(
        @JsonProperty("uid") FaultUid uid,
        @JsonProperty("mode") FailureMode mode) {

    @JsonIgnore
    public boolean isTransient() {
        return uid.getPoint().isTransient();
    }

    @JsonIgnore
    public boolean isPersistent() {
        return uid.getPoint().isPersistent();
    }

    @JsonIgnore
    public Behaviour asBehaviour() {
        return new Behaviour(uid, mode);
    }

    public boolean matches(Fault other) {
        if (!uid.matches(other.uid())) {
            return false;
        }

        return mode.equals(other.mode());
    }

    // if a <= b
    public static boolean isSubsetOf(Set<Fault> subset, Set<Fault> superset) {
        return Sets.isSubsetOf(subset, superset, Fault::matches);
    }

    public static Set<Fault> allFaults(FaultUid point, Collection<FailureMode> modes) {
        return modes.stream()
                .map(mode -> new Fault(point, mode))
                .collect(Collectors.toSet());
    }

    public static Set<Set<Fault>> allFaults(Collection<FaultUid> point, Collection<FailureMode> modes) {
        return allFaults(List.copyOf(point), Set.of(), modes);
    }

    private static Set<Set<Fault>> allFaults(List<FaultUid> uids, Set<Fault> current,
            Collection<FailureMode> modes) {
        if (uids.isEmpty()) {
            return Set.of(current);
        }

        var head = uids.get(0);
        var tail = uids.subList(1, uids.size());
        Set<Fault> additions = allFaults(head, modes);
        Set<Set<Fault>> allSets = new LinkedHashSet<>();
        for (var addition : additions) {
            Set<Fault> newCurrent = Sets.plus(current, addition);
            allSets.addAll(allFaults(tail, newCurrent, modes));
        }

        return allSets;
    }
}
