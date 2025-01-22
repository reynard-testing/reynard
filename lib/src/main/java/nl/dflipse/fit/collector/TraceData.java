
package nl.dflipse.fit.collector;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonDeserialize
public class TraceData {
    @JsonProperty("spans")
    public List<TraceSpan> spans;

    @JsonProperty("trees")
    public List<TraceTreeSpan> trees;
}
