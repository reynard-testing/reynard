package nl.dflipse.fit.trace.tree;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import nl.dflipse.fit.faultload.FaultUid;

import com.fasterxml.jackson.annotation.JsonProperty;

@JsonSerialize
@JsonDeserialize
public class TraceTreeSpan {
    @JsonProperty("children")
    public List<TraceTreeSpan> children;

    @JsonProperty("span")
    public TraceSpan span;

    @JsonProperty("report")
    public TraceSpanReport report;

    public TraceTreeSpan applyMask(FaultUid mask) {
        TraceTreeSpan masked = new TraceTreeSpan();
        masked.children = children.stream()
                .map(c -> c.applyMask(mask))
                .collect(Collectors.toList());
        masked.span = span;
        if (report != null) {
            masked.report = report.applyMask(mask);
        }
        return masked;
    }

    public boolean hasReport() {
        return report != null;
    }
}
