package nl.dflipse.fit.trace.tree;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.FailureMode;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonSerialize
@JsonDeserialize
public class TraceSpanReport {
    @JsonProperty("trace_id")
    public String traceId;

    @JsonProperty("span_id")
    public String spanId;

    @JsonProperty("uid")
    public FaultUid faultUid;

    @JsonProperty("concurrent_to")
    public List<FaultUid> concurrentTo;

    @JsonProperty("injected_fault")
    public Fault injectedFault;

    @JsonProperty("is_initial")
    public boolean isInitial;

    @JsonProperty("response")
    public TraceSpanResponse response;

    public boolean hasError() {
        return injectedFault != null || (response != null && response.isErrenous());
    }

    public boolean hasIndirectError() {
        return injectedFault == null && response != null && response.isErrenous();
    }

    public Fault getRepresentativeFault() {
        if (injectedFault != null) {
            return injectedFault;
        }

        if (!response.isErrenous()) {
            return null;
        }

        FailureMode faultMode = new FailureMode(ErrorFault.FAULT_TYPE, List.of("" + response.status));
        return new Fault(faultUid, faultMode);
    }
}
