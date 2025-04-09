package nl.dflipse.fit.faultload;

import nl.dflipse.fit.faultload.faultmodes.FailureMode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonDeserialize
public record Fault(
        @JsonProperty("uid") FaultUid uid,
        @JsonProperty("mode") FailureMode mode) {

    public boolean isTransient() {
        return uid.isTransient();
    }

    public boolean isPersistent() {
        return uid.isPersistent();
    }
}
