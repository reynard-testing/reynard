package io.github.delanoflipse.fit.suite.faultload;

import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.github.delanoflipse.fit.suite.strategy.util.Lists;

@JsonSerialize
@JsonDeserialize
public record FaultUid(List<FaultInjectionPoint> stack) {
    public FaultUid {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("Stack must not be null and must have at least one element.");
        }
    }

    @JsonIgnore
    public int count() {
        return getPoint().count();
    }

    @JsonIgnore
    public String destination() {
        return getPoint().destination();
    }

    @JsonIgnore
    public String signature() {
        return getPoint().signature();
    }

    @JsonIgnore
    public FaultUid asAnyPayload() {
        var head = getPoint();
        var tail = getTail();

        return new FaultUid(Lists.plus(tail, head.asAnyPayload()));
    }

    /** Whether all points are without persistent faults */
    @JsonIgnore
    public boolean isNormalForm() {
        var head = getPoint();

        if (head.isPersistent()) {
            return false;
        }

        if (hasParent(false)) {
            return getParent(false).isNormalForm();
        } else {
            return true;
        }
    }

    @JsonIgnore
    public boolean hasParent() {
        return hasParent(false);
    }

    @JsonIgnore
    public boolean hasParent(boolean includeRoot) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (!includeRoot && stack.size() == 1) {
            return false;
        }

        return true;
    }

    @JsonIgnore
    public FaultUid getParent() {
        return getParent(false);
    }

    @JsonIgnore
    public FaultUid getParent(boolean includeRoot) {
        if (!hasParent(includeRoot)) {
            return null;
        }

        return new FaultUid(getTail());
    }

    @JsonIgnore
    public FaultUid asAnyCount() {
        var head = getPoint();
        var tail = getTail();

        return new FaultUid(Lists.plus(tail, head.asAnyCount()));
    }

    @JsonIgnore
    public FaultUid withoutCallStack() {
        List<FaultInjectionPoint> without = stack.stream()
                .map(x -> x.asAnyCallStack())
                .toList();

        return new FaultUid(without);
    }

    @JsonIgnore
    public boolean isTransient() {
        return getPoint().isTransient();
    }

    @JsonIgnore
    public boolean isPersistent() {
        return getPoint().isPersistent();
    }

    @Override
    public String toString() {
        List<String> stackStrings = getTail().stream()
                .map(FaultInjectionPoint::toSimplifiedString)
                .toList();

        if (stackStrings.isEmpty()) {
            return getPoint().toString();
        }

        return String.join(">", stackStrings) + ">" + getPoint().toString();
    }

    @JsonIgnore
    public FaultInjectionPoint getPoint() {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.get(stack.size() - 1);
    }

    @JsonIgnore
    public List<FaultInjectionPoint> getTail() {
        return stack.subList(0, stack.size() - 1);
    }

    @JsonIgnore
    public boolean isRoot() {
        return stack.isEmpty();
    }

    @JsonIgnore
    public boolean isInitial() {
        return stack.size() == 1;
    }

    private boolean matches(List<FaultInjectionPoint> a, List<FaultInjectionPoint> b, boolean ignoreCount) {
        if (a == null || b == null) {
            return false;
        }

        if (a.size() != b.size()) {
            return false;
        }

        for (int i = 0; i < a.size(); i++) {
            if (ignoreCount) {
                if (!a.get(i).matchesUpToCount(b.get(i))) {
                    return false;
                }
            } else {
                if (!a.get(i).matches(b.get(i))) {
                    return false;
                }

            }
        }

        return true;
    }

    public boolean matches(FaultUid other) {
        if (other == null) {
            return false;
        }

        return matches(stack, other.stack, false);
    }

    public boolean matchesUpToCount(FaultUid other) {
        if (other == null) {
            return false;
        }

        return matches(stack, other.stack, true);
    }

    public static boolean contains(Collection<FaultUid> collection, FaultUid uid) {
        if (collection == null || uid == null) {
            return false;
        }

        for (FaultUid other : collection) {
            if (other.matches(uid)) {
                return true;
            }
        }

        return false;
    }
}
