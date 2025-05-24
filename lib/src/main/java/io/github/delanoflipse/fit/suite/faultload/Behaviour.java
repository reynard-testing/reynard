package io.github.delanoflipse.fit.suite.faultload;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.github.delanoflipse.fit.suite.faultload.modes.FailureMode;
import io.github.delanoflipse.fit.suite.strategy.util.Sets;

@JsonSerialize
public record Behaviour(FaultUid uid, FailureMode mode) {

    @JsonIgnore
    public boolean isFault() {
        return mode != null;
    }

    @JsonIgnore
    public boolean isHappyPath() {
        return mode == null;
    }

    @JsonIgnore
    public Fault getFault() {
        if (mode == null) {
            return null;
        }
        return new Fault(uid, mode);
    }

    public boolean matches(Behaviour other) {
        boolean modeMatches = mode == null ? other.mode == null : mode.equals(other.mode);

        if (!modeMatches) {
            return false;
        }

        boolean uidMatches = uid.matches(other.uid);
        return uidMatches;
    }

    @JsonIgnore
    public Behaviour asMode(FailureMode newMode) {
        return new Behaviour(uid, newMode);
    }

    @JsonIgnore
    public Behaviour asLocalised() {
        return new Behaviour(uid.asLocalised(), mode);
    }

    public static Behaviour of(FaultUid uid) {
        return new Behaviour(uid, null);
    }

    public static Set<Behaviour> of(Set<Fault> uids) {
        return uids.stream()
                .map(Fault::asBehaviour)
                .collect(Collectors.toSet());
    }

    public static List<Behaviour> of(List<Fault> uids) {
        return uids.stream()
                .map(Fault::asBehaviour)
                .collect(Collectors.toList());
    }

    public static boolean matches(Set<Behaviour> a, Set<Behaviour> b) {
        return isSubsetOf(a, b) && isSubsetOf(b, a);
    }

    // if a <= b
    public static boolean isSubsetOf(Collection<Behaviour> subset, Collection<Behaviour> superset) {
        return Sets.isSubsetOf(subset, superset, Behaviour::matches);
    }

    public static Set<FaultUid> getFaultUids(Collection<Behaviour> behaviours) {
        return behaviours.stream()
                .map(Behaviour::uid)
                .collect(Collectors.toSet());
    }

    public static boolean contains(Collection<Behaviour> collection, Behaviour uid) {
        if (collection == null || uid == null) {
            return false;
        }

        for (Behaviour other : collection) {
            if (other.matches(uid)) {
                return true;
            }
        }

        return false;
    }

    public static boolean contains(Collection<Behaviour> collection, FaultUid uid) {
        if (collection == null || uid == null) {
            return false;
        }

        for (Behaviour other : collection) {
            if (other.uid().matches(uid)) {
                return true;
            }
        }

        return false;
    }
}
