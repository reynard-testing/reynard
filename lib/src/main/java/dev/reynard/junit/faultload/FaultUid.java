package dev.reynard.junit.faultload;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import dev.reynard.junit.strategy.util.Lists;

@JsonSerialize
@JsonDeserialize
public record FaultUid(List<FaultInjectionPoint> stack) {
    public FaultUid {
        // Shape must be:
        // [nil] -> any point
        // [nil, point] -> point regardless of causal origin
        // [point, point...] -> point with causal origin
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("Stack must not be null and must have at least one element.");
        }

        if (stack.get(0) == null) {
            if (stack.size() == 1) {
                // [nil] is allowed
            }
            // [nil, point] is allowed
            if (stack.size() > 2) {
                throw new IllegalArgumentException("Stack must have at most two elements if first element is null.");
            }

            if (stack.size() == 2 && stack.get(1) == null) {
                throw new IllegalArgumentException(
                        "Second element of stack must not be null if first element is null.");
            }
        } else {
            if (stack.stream().anyMatch(x -> x == null)) {
                throw new IllegalArgumentException(
                        "Only the first element of stack may be null to indicate a wildcard!");
            }
        }
    }

    public static FaultUid Any() {
        return new FaultUid(Arrays.asList((FaultInjectionPoint) null));
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

    @JsonIgnore
    public boolean isAnyStack() {
        if (stack == null || stack.isEmpty() || stack.size() == 1) {
            return false;
        }

        if (stack.size() > 1) {
            return stack.get(0) == null;
        }

        return false;
    }

    @JsonIgnore
    public boolean isAny() {
        return stack != null && stack.size() == 1 && stack.get(0) == null;
    }

    /** Whether all points are without query */
    @JsonIgnore
    public boolean isNormalForm() {
        if (isAny() || isAnyStack()) {
            return false;
        }

        var head = getPoint();

        // Note anyPredecessors() is allowed (as long it is used persistently)
        if (head.isPersistent() || head.isAnyDestination() || head.isAnySignature() || head.isAnyPayload()) {
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

        if (stack.get(0) == null) {
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
    public FaultUid asAnyPredecessors() {
        var head = getPoint();
        var tail = getTail();

        return new FaultUid(Lists.plus(tail, head.asAnyPredecessors()));
    }

    @JsonIgnore
    public FaultUid asAnySignature() {
        var head = getPoint();
        var tail = getTail();

        return new FaultUid(Lists.plus(tail, head.asAnySignature()));
    }

    @JsonIgnore
    public FaultUid asAnyDestination() {
        var head = getPoint();
        var tail = getTail();

        return new FaultUid(Lists.plus(tail, head.asAnyDestination()));
    }

    @JsonIgnore
    public FaultUid asAnyOrigin() {
        var head = getPoint();

        return new FaultUid(Arrays.asList(null, head.asAnyDestination()));
    }

    @JsonIgnore
    public FaultUid withCount(int count) {
        var head = getPoint();
        var tail = getTail();

        return new FaultUid(Lists.plus(tail, head.withCount(count)));
    }

    @JsonIgnore
    public FaultUid withoutPredecessors() {
        List<FaultInjectionPoint> without = stack.stream()
                .map(x -> x.asAnyPredecessors())
                .toList();

        return new FaultUid(without);
    }

    @JsonIgnore
    public FaultUid asLocalised() {
        if (stack.size() <= 2) {
            return this;
        }

        FaultInjectionPoint origin = getOrigin().asAnyCount().asAnyPredecessors();
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

        FaultInjectionPoint point = stack.get(stack.size() - 1);
        if (point == null) {
            return FaultInjectionPoint.Any();
        }

        return point;
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

        // cannot compare without predecessors
        if (pointSelf.isAnyPredecessors() || pointOther.isAnyPredecessors()) {
            return Optional.empty();
        }

        return Optional.of(FaultInjectionPoint.isBefore(pointSelf.predecessors(), pointOther.predecessors()));
    }

    private boolean matches(FaultInjectionPoint a, FaultInjectionPoint b, boolean ignoreCount) {
        if (ignoreCount) {
            return a.matchesUpToCount(b);
        } else {
            return a.matches(b);
        }
    }

    private boolean matches(List<FaultInjectionPoint> a, List<FaultInjectionPoint> b, boolean ignoreCount) {
        if (a == null || b == null) {
            return false;
        }

        if (a.size() != b.size()) {
            return false;
        }

        for (int i = 0; i < a.size(); i++) {
            var ap = a.get(i);
            var bp = b.get(i);

            if (ap == null || bp == null) {
                throw new IllegalArgumentException(
                        "FaultUid stack must not contain null elements except for the first element.");
            }

            if (!matches(ap, bp, ignoreCount)) {
                return false;
            }
        }

        return true;
    }

    public boolean matches(FaultUid other) {
        if (other == null) {
            return false;
        }

        if (isAny() || other.isAny()) {
            return true; // Any uid matches any other uid
        }

        if (isAnyStack()) {
            return matches(getPoint(), other.getPoint(), false);
        }

        if (other.isAnyStack()) {
            return matches(getPoint(), other.getPoint(), false);
        }

        return matches(stack, other.stack, false);
    }

    public boolean matchesUpToCount(FaultUid other) {
        if (other == null) {
            return false;
        }

        if (isAny() || other.isAny()) {
            return true; // Any uid matches any other uid
        }

        if (isAnyStack()) {
            return matches(getPoint(), other.getPoint(), true);
        }

        if (other.isAnyStack()) {
            return matches(getPoint(), other.getPoint(), true);
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
