package nl.dflipse.fit.faultload;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonDeserialize
public record PartialFaultInjectionPoint(String destination, String signature, String payload) {

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
        return destination + ":" + signature + payloadStr;
    }

    public PartialFaultInjectionPoint asAnyPayload() {
        return new PartialFaultInjectionPoint(destination, signature, "*");
    }

    private boolean matches(String a, String b) {
        return a == null || b == null || a.equals("*") || b.equals("*") || a.equals(b);
    }

    public boolean matches(PartialFaultInjectionPoint other) {
        return matches(destination, other.destination) &&
                matches(signature, other.signature) &&
                matches(payload, other.payload);

    }
}
