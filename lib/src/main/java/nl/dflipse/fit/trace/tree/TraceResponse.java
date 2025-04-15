package nl.dflipse.fit.trace.tree;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonSerialize
@JsonDeserialize
public class TraceResponse {
    @JsonProperty("status")
    public int status;

    @JsonProperty("body")
    public String body;

    @JsonProperty("duration_ms")
    public int durationMs;

    public boolean isErrenous() {
        return !(status >= 200 && status < 300);
    }

    public boolean equals(TraceResponse other) {
        if (other == null) {
            return false;
        }
        return status == other.status && body.equals(other.body);
    }
}
