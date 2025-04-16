package nl.dflipse.fit.faultload;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonDeserialize
public record FaultInjectionPoint(String destination, String signature, String payload, int count) {

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
        return destination + ":" + signature + payloadStr + countStr;
    }

    public String toSimplifiedString() {
        String countStr = count < 0 ? "#∞" : ("#" + count);
        return destination + countStr;
    }

    public FaultInjectionPoint asAnyPayload() {
        return new FaultInjectionPoint(destination, signature, "*", count);
    }

    public FaultInjectionPoint asAnyCount() {
        return new FaultInjectionPoint(destination, signature, payload, -1);
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

    public boolean matches(FaultInjectionPoint other) {
        return matchesUpToCount(other) &&
                matches(count, other.count);
    }

    public boolean matchesUpToCount(FaultInjectionPoint other) {
        return matches(destination, other.destination) &&
                matches(signature, other.signature) &&
                matches(payload, other.payload);
    }
}
