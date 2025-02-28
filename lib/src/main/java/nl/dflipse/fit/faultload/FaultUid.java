package nl.dflipse.fit.faultload;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonDeserialize
public record FaultUid(String origin, String destination, String signature, String payload, int count) {
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
