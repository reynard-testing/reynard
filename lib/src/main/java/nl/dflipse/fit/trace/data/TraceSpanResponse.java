package nl.dflipse.fit.trace.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonSerialize
@JsonDeserialize
public class TraceSpanResponse {
    @JsonProperty("status")
    public int status;

    @JsonProperty("body")
    public String body;
}
