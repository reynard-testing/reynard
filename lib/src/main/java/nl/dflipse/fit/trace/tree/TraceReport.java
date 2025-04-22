package nl.dflipse.fit.trace.tree;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.modes.ErrorFault;
import nl.dflipse.fit.faultload.modes.FailureMode;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonSerialize
@JsonDeserialize
public class TraceReport {
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
    public TraceResponse response;

    public boolean hasFaultBehaviour() {
        return injectedFault != null || (response != null && response.isErrenous());
    }

    public boolean hasIndirectFaultBehaviour() {
        return injectedFault == null && response != null && response.isErrenous();
    }

    public Behaviour getBehaviour() {
        Fault fault = getFault();
        if (fault == null) {
            return new Behaviour(faultUid, null);
        }

        return new Behaviour(faultUid, fault.mode());
    }

    public Fault getFault() {
        if (injectedFault != null) {
            return injectedFault;
        }

        if (response == null || !response.isErrenous()) {
            return null;
        }

        FailureMode faultMode = new FailureMode(ErrorFault.FAULT_TYPE, List.of("" + response.status));
        return new Fault(faultUid, faultMode);
    }
}
