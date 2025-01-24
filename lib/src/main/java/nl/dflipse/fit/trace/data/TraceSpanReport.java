package nl.dflipse.fit.trace.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonSerialize
@JsonDeserialize
public class TraceSpanReport {
    @JsonProperty("span_id")
    public String spanId;

    @JsonProperty("span_uid")
    public String spanUid;

    @JsonProperty("fault_injected")
    public boolean faultInjected;
}
