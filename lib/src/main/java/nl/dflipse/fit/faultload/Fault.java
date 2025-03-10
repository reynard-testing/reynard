package nl.dflipse.fit.faultload;

import nl.dflipse.fit.faultload.faultmodes.FaultMode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonDeserialize
public record Fault(
    @JsonProperty("uid") FaultUid uid,
    @JsonProperty("mode") FaultMode mode) {
  public FaultUid getUid() {
    return uid;
  }

  public FaultMode getMode() {
    return mode;
  }

  public Fault applyMask(FaultUid mask) {
    return new Fault(uid.applyMask(mask), mode);
  }
}
