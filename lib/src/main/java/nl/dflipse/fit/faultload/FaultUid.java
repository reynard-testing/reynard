package nl.dflipse.fit.faultload;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonDeserialize
public record FaultUid(String origin, String destination, String signature, int count) {
    public String origin() {
        return origin;
    }

    public String destination() {
        return destination;
    }

    public String signature() {
        return signature;
    }

    public int count() {
        return count;
    }

    public String toString() {
        return origin + ">" + destination + ":" + signature + "|" + count;
    }
}
