package nl.dflipse.fit.collector;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import nl.dflipse.fit.trace.TraceState;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonSerialize
@JsonDeserialize
public class TraceSpan {
    @JsonProperty("name")
    public String name;

    @JsonProperty("service_name")
    public String serviceName;

    @JsonProperty("parent_span_id")
    public String parentSpanId;

    @JsonProperty("span_uid")
    public String spanUid;

    @JsonProperty("span_id")
    public String spanId;

    @JsonProperty("trace_id")
    public String traceId;

    @JsonProperty("start_time")
    public long startTime;

    @JsonProperty("end_time")
    public long endTime;

    @JsonProperty("trace_state")
    public TraceState traceState;

    @JsonProperty("is_error")
    public boolean isError;

    @JsonProperty("error_message")
    public String errorMessage;

    @JsonSetter("trace_state")
    private void setTraceState(String data) {
        if (data == null) {
            return;
        }
        traceState = new TraceState(data);
    }

    @JsonGetter("trace_state")
    private String getTraceState() {
        if (traceState == null) {
            return null;
        }
        return traceState.toString();
    }
}
