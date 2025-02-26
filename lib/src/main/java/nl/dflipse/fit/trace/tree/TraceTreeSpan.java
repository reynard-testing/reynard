package nl.dflipse.fit.trace.tree;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.trace.util.TraceTraversal;

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

    public boolean isIncomplete() {
        boolean incomplete = false;

        TraceTraversal.any(this, (TraceTreeSpan node) -> {
            if (node.report != null && node.report.response == null) {
                return true;
            }

            return false;
        });

        return incomplete;
    }

    public Set<Fault> getFaults() {
        Set<Fault> faults = new HashSet<>();

        TraceTraversal.any(this, (TraceTreeSpan node) -> {
            if (node.report != null && node.report.injectedFault != null) {
                faults.add(node.report.injectedFault);
            }

            return false;
        });

        return faults;
    }

    public Set<FaultUid> getFaultUids() {
        Set<FaultUid> faults = new HashSet<>();

        TraceTraversal.any(this, (TraceTreeSpan node) -> {
            if (node.report != null) {
                faults.add(node.report.faultUid);
            }

            return false;
        });

        return faults;
    }

    public TraceTreeSpan findNode(Fault fault) {
        return TraceTraversal.find(this, (TraceTreeSpan node) -> {
            if (node.report != null && node.report.injectedFault != null && node.report.injectedFault.equals(fault)) {
                return true;
            }

            return false;
        });
    }

    public TraceTreeSpan findNode(FaultUid faultUid) {
        return TraceTraversal.find(this, (TraceTreeSpan node) -> {
            if (node.report != null && node.report.faultUid != null && node.report.faultUid.equals(faultUid)) {
                return true;
            }

            return false;
        });
    }
}
