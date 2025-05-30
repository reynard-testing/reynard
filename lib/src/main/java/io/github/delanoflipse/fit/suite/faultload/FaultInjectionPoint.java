package io.github.delanoflipse.fit.suite.faultload;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.github.delanoflipse.fit.suite.strategy.util.Sets;

@JsonSerialize
@JsonDeserialize
public record FaultInjectionPoint(String destination, String signature, String payload,
        @JsonProperty("call_stack") Map<String, Integer> callStack, int count) {
    private static final String ANY_WILDCARD = "*";

    public FaultInjectionPoint {
        // Ensure map is immutable
        if (callStack != null) {
            callStack = Collections.unmodifiableMap(callStack);
        }
    }

    public static FaultInjectionPoint Any() {
        return new FaultInjectionPoint(ANY_WILDCARD, ANY_WILDCARD, ANY_WILDCARD, null, -1);
    }

    @JsonIgnore
    public boolean isAnyDestination() {
        return destination.equals(ANY_WILDCARD);
    }

    @JsonIgnore
    public boolean isAnyCallStack() {
        return callStack == null;
    }

    @JsonIgnore
    public boolean isAnySignature() {
        return signature.equals(ANY_WILDCARD);
    }

    @JsonIgnore
    public boolean isAnyPayload() {
        return payload.equals(ANY_WILDCARD);
    }

    // Builder patterns
    public FaultInjectionPoint withDestination(String destination) {
        return new FaultInjectionPoint(destination, signature, payload, callStack, count);
    }

    public FaultInjectionPoint withSignature(String signature) {
        return new FaultInjectionPoint(destination, signature, payload, callStack, count);
    }

    public FaultInjectionPoint withPayload(String payload) {
        return new FaultInjectionPoint(destination, signature, payload, callStack, count);
    }

    public FaultInjectionPoint withCallStack(Map<String, Integer> callStack) {
        return new FaultInjectionPoint(destination, signature, payload, callStack, count);
    }

    public FaultInjectionPoint withCount(int count) {
        return new FaultInjectionPoint(destination, signature, payload, callStack, count);
    }

    @Override
    public String toString() {
        String payloadStr = (payload.equals("*") || payload.equals("")) ? "" : "(" + payload + ")";
        String countStr = count < 0 ? "#∞" : ("#" + count);

        // {key1:value1,key2:value2, ...}
        String csStr = "";
        if (callStack != null) {
            csStr = "{" + callStack.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + ":" + e.getValue())
                    .reduce((a, b) -> a + "," + b).orElse("") + "}";
        }

        return destination + ":" + signature + payloadStr + csStr + countStr;
    }

    @JsonIgnore
    public String toSimplifiedString() {
        String countStr = count < 0 ? "#∞" : ("#" + count);
        return destination + countStr;
    }

    @JsonIgnore
    public FaultInjectionPoint asAnyPayload() {
        return new FaultInjectionPoint(destination, signature, "*", callStack, count);
    }

    @JsonIgnore
    public FaultInjectionPoint asAnyCount() {
        return new FaultInjectionPoint(destination, signature, payload, callStack, -1);
    }

    @JsonIgnore
    public FaultInjectionPoint asAnyCallStack() {
        return new FaultInjectionPoint(destination, signature, payload, null, count);
    }

    @JsonIgnore
    public PartialFaultInjectionPoint asPartial() {
        return new PartialFaultInjectionPoint(destination, signature, payload);
    }

    @JsonIgnore
    public boolean isTransient() {
        return count >= 0;
    }

    @JsonIgnore
    public boolean isPersistent() {
        return count < 0;
    }

    // cs1 < cs2
    public static boolean isBefore(Map<String, Integer> cs1, Map<String, Integer> cs2) {
        Set<String> keys = Sets.union(cs1.keySet(), cs2.keySet());
        boolean hasOneBefore = false;

        for (String key : keys) {
            if (!cs1.containsKey(key)) {
                // cs1 has an event that cs2 has not seen
                hasOneBefore = true;
                break;
            }

            if (!cs2.containsKey(key)) {
                // cs2 has an event that cs1 has not seen
                return false;
            }

            int v1 = cs1.get(key);
            int v2 = cs2.get(key);

            if (v2 > v1) {
                hasOneBefore = true;
            }

            if (v1 > v2) {
                // cs1 has an event that is after cs2
                return false;
            }
        }

        // All events are <=, and one is before
        return hasOneBefore;
    }

    private boolean matches(String a, String b) {
        return a == null || b == null || a.equals("*") || b.equals("*") || a.equals(b);
    }

    private boolean matches(int a, int b) {
        return a < 0 || b < 0 || a == b;
    }

    private boolean matches(Map<String, Integer> a, Map<String, Integer> b) {
        if (a == null || b == null) {
            return true;
        }

        if (a.size() != b.size()) {
            return false;
        }

        return a.equals(b);
    }

    public boolean matches(FaultInjectionPoint other) {
        return matchesUpToCount(other) &&
                matches(count, other.count);
    }

    public boolean matchesUpToCount(FaultInjectionPoint other) {
        return matches(destination, other.destination) &&
                matches(signature, other.signature) &&
                matches(callStack, other.callStack) &&
                matches(payload, other.payload);
    }
}
