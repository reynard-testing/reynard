package io.github.delanoflipse.fit.suite.faultload;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /** Whether all points are without query */
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
    public FaultUid asChild(FaultInjectionPoint point) {
        return new FaultUid(Lists.plus(stack, point));
    }

    @JsonIgnore
    public FaultUid asAnyCount() {
        var head = getPoint();
        var tail = getTail();

        return new FaultUid(Lists.plus(tail, head.asAnyCount()));
    }

    @JsonIgnore
    public FaultUid asAnyCallStack() {
        var head = getPoint();
        var tail = getTail();

        return new FaultUid(Lists.plus(tail, head.asAnyCallStack()));
    }

    @JsonIgnore
    public FaultUid withCount(int count) {
        var head = getPoint();
        var tail = getTail();

        return new FaultUid(Lists.plus(tail, head.withCount(count)));
    }

    @JsonIgnore
    public FaultUid withoutCallStack() {
        List<FaultInjectionPoint> without = stack.stream()
                .map(x -> x.asAnyCallStack())
                .toList();

        return new FaultUid(without);
    }

    @JsonIgnore
    public FaultUid asLocalised() {
        if (stack.size() <= 2) {
            return this;
        }

        FaultInjectionPoint origin = getOrigin().asAnyCount().asAnyCallStack();
        FaultInjectionPoint destination = getPoint();

        return new FaultUid(List.of(origin, destination));
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
    public FaultInjectionPoint getOrigin() {
        if (stack == null || stack.size() < 2) {
            return null;
        }
        return stack.get(stack.size() - 2);
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

    @JsonIgnore
    public Optional<Boolean> isBefore(FaultUid other) {
        // Points cannot be compared
        if (!getParent().matches(other.getParent())) {
            return Optional.empty();
        }

        FaultInjectionPoint pointOther = other.getPoint();
        FaultInjectionPoint pointSelf = getPoint();

        FaultUid withoutCount = other.asAnyCount();
        int countOther = pointOther.count();
        int countSelf = pointSelf.count();
        boolean isCountBefore = countSelf < countOther;

        if (matches(withoutCount)) {
            // Same uid up to count, then it must be lower
            return Optional.of(isCountBefore);
        }

        // cannot compare without call stack
        if (pointSelf.isAnyCallStack() || pointOther.isAnyCallStack()) {
            return Optional.empty();
        }

        return Optional.of(FaultInjectionPoint.isBefore(pointSelf.callStack(), pointOther.callStack()));
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
