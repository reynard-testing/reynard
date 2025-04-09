package nl.dflipse.fit.faultload;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import nl.dflipse.fit.strategy.util.Lists;

@JsonSerialize
@JsonDeserialize
public record FaultUid(List<FaultInjectionPoint> stack) {

    public int count() {
        return getPoint().count();
    }

    public String destination() {
        return getPoint().destination();
    }

    public String signature() {
        return getPoint().signature();
    }

    public FaultUid asAnyPayload() {
        var head = getPoint();
        var tail = getTail();

        return new FaultUid(Lists.add(tail, head.asAnyPayload()));
    }

    public FaultUid asAnyCount() {
        var head = getPoint();
        var tail = getTail();

        return new FaultUid(Lists.add(tail, head.asAnyCount()));
    }

    public boolean isTransient() {
        return getPoint().isTransient();
    }

    public boolean isPersistent() {
        return getPoint().isPersistent();
    }

    @Override
    public String toString() {
        List<String> stackStrings = stack.stream()
                .map(FaultInjectionPoint::toString)
                .toList();
        return String.join(">", stackStrings);
    }

    public FaultInjectionPoint getPoint() {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.get(stack.size() - 1);
    }

    public List<FaultInjectionPoint> getTail() {
        return stack.subList(0, stack.size() - 1);
    }

    public boolean isFromInitial() {
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
}
