package nl.dflipse.fit.faultload;

import java.util.Set;

import nl.dflipse.fit.faultload.modes.FailureMode;

public record Behaviour(FaultUid uid, FailureMode mode) {

    public boolean isFault() {
        return mode != null;
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
}
