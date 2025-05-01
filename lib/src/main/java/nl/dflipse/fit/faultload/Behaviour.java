package io.github.delanoflipse.fit.suite.faultload;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.delanoflipse.fit.suite.faultload.modes.FailureMode;

public record Behaviour(FaultUid uid, FailureMode mode) {

    public boolean isFault() {
        return mode != null;
    }

    public boolean isHappyPath() {
        return mode == null;
    }

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
    public static boolean isSubsetOf(Set<Behaviour> subset, Set<Behaviour> superset) {
        if (subset == null || superset == null) {
            return false;
        }

        if (subset.size() > superset.size()) {
            return false;
        }

        for (Behaviour a : subset) {
            boolean found = false;

            for (Behaviour b : superset) {
                if (a.matches(b)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }

    public static Set<FaultUid> getFaultUids(Collection<Behaviour> behaviours) {
        return behaviours.stream()
                .map(Behaviour::uid)
                .collect(Collectors.toSet());
    }
}
