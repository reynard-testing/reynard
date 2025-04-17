package nl.dflipse.fit.trace.tree;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

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

    @Override
    public String toString() {
        return "TraceResponse[status="+status+",duration_ms="+durationMs+", body=\""+body+"\"]";
    }
}
