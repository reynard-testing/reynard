package dev.reynard.junit.faultload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonDeserialize
public record PartialFaultInjectionPoint(String destination, String signature, String payload) {
    @JsonIgnore
    public boolean isAnyDestination() {
        return destination == null;
    }

    @JsonIgnore
    public boolean isAnySignature() {
        return signature == null;
    }

    @JsonIgnore
    public boolean isAnyPayload() {
        return payload == null;
    }

    @Override
    public String toString() {
        String payloadStr = (payload == null || payload.isEmpty()) ? ""
                : "(" + payload.substring(0, Math.min(8, payload.length())) + ")";
        String signatureStr = signature == null ? "" : signature;
        String destinationStr = destination == null ? "" : destination;
        return destinationStr + ":" + signatureStr + payloadStr;
    }

    @JsonIgnore
    public PartialFaultInjectionPoint asAnyPayload() {
        return new PartialFaultInjectionPoint(destination, signature, null);
    }

    private boolean matches(String a, String b) {
        return a == null || b == null || a.equals(b);
    }

    public boolean matches(PartialFaultInjectionPoint other) {
        return matches(destination, other.destination) &&
                matches(signature, other.signature) &&
                matches(payload, other.payload);

    }
}
