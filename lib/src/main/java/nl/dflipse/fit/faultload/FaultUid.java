package nl.dflipse.fit.faultload;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonDeserialize
public record FaultUid(String origin, String destination, String signature, String payload, int count) {

    public static FaultUid payloadMask = new FaultUid(null, null, null, "*", 0);

    public String origin() {
        return origin;
    }

    public String destination() {
        return destination;
    }

    public String signature() {
        return signature;
    }

    public String payload() {
        return payload;
    }

    public int count() {
        return count;
    }

    public FaultUid asAnyPayload() {
        return new FaultUid(origin, destination, signature, "*", count);
    }

    private boolean isMasked(String value) {
        return value == null || value.equals("*");
    }

    private boolean isMasked(int value) {
        return value < 0;
    }

    public FaultUid applyMask(FaultUid mask) {
        if (mask == null) {
            return this;
        }
        String maskedOrigin = isMasked(mask.origin) ? mask.origin : origin;
        String maskedDestination = isMasked(mask.destination) ? mask.destination : destination;
        String maskedSignature = isMasked(mask.signature) ? mask.signature : signature;
        String maskedPayload = isMasked(mask.payload) ? mask.payload : payload;
        int maskedCount = isMasked(mask.count) ? mask.count : count;
        return new FaultUid(maskedOrigin, maskedDestination, maskedSignature, maskedPayload, maskedCount);
    }

    public String toString() {
        return origin + ">" + destination + ":" + signature + "(" + payload + ")#" + count;
    }

    private boolean matches(String a, String b) {
        return a == null || b == null || a == "*" || b == "*" || a.equals(b);
    }

    private boolean matches(int a, int b) {
        return a < 0 || b < 0 || a == b;
    }

    public boolean matches(FaultUid other) {
        return matches(origin, other.origin) &&
                matches(destination, other.destination) &&
                matches(signature, other.signature) &&
                matches(payload, other.payload) &&
                matches(count, other.count);
    }
}
