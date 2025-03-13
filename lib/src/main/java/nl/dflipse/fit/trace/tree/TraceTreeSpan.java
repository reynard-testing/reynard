package nl.dflipse.fit.trace.tree;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonDeserialize
public class TraceTreeSpan {
    @JsonProperty("children")
    public List<TraceTreeSpan> children;

    @JsonProperty("span")
    public TraceSpan span;

    @JsonProperty("reports")
    public List<TraceSpanReport> reports;

    public boolean hasReport() {
        return !reports.isEmpty();
    }
}
