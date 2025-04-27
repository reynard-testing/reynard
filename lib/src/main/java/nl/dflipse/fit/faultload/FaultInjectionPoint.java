package nl.dflipse.fit.faultload;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonDeserialize
public record FaultInjectionPoint(String destination, String signature, String payload,
        @JsonProperty("vector_clock") Map<String, Integer> vectorClock, int count) {

    public FaultInjectionPoint {
        // Ensure map is immutable
        vectorClock = Collections.unmodifiableMap(vectorClock);
    }

    private static String ANY_WILDCARD = "*";

    public boolean isAnyDestination() {
        return destination.equals(ANY_WILDCARD);
    }

    public boolean isAnySignature() {
        return signature.equals(ANY_WILDCARD);
    }

    public boolean isAnyPayload() {
        return payload.equals(ANY_WILDCARD);
    }

    @Override
    public String toString() {
        String payloadStr = (payload.equals("*") || payload.equals("")) ? "" : "(" + payload + ")";
        String countStr = count < 0 ? "#∞" : ("#" + count);

        // {key1:value1,key2:value2, ...}
        String vcStr = "";
        if (vectorClock != null && !vectorClock.isEmpty()) {
            vcStr = "{" + vectorClock.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey().toString() + ":" + e.getValue())
                    .reduce((a, b) -> a + "," + b).orElse("") + "}";
        }

        return destination + ":" + signature + payloadStr + vcStr + countStr;
    }

    public String toSimplifiedString() {
        String countStr = count < 0 ? "#∞" : ("#" + count);
        return destination + countStr;
    }

    public FaultInjectionPoint asAnyPayload() {
        return new FaultInjectionPoint(destination, signature, "*", vectorClock, count);
    }

    public FaultInjectionPoint asAnyCount() {
        return new FaultInjectionPoint(destination, signature, payload, vectorClock, -1);
    }

    public PartialFaultInjectionPoint asPartial() {
        return new PartialFaultInjectionPoint(destination, signature, payload);
    }

    public boolean isTransient() {
        return count >= 0;
    }

    public boolean isPersistent() {
        return count < 0;
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
                matches(vectorClock, other.vectorClock) &&
                matches(payload, other.payload);
    }
}
