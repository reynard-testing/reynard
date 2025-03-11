package nl.dflipse.fit.instrument.controller;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import nl.dflipse.fit.trace.tree.TraceSpan;
import nl.dflipse.fit.trace.tree.TraceSpanReport;
import nl.dflipse.fit.trace.tree.TraceTreeSpan;

@JsonDeserialize
public class ControllerResponse {

    @JsonProperty("spans")
    public List<TraceSpan> spans;

    @JsonProperty("trees")
    public List<TraceTreeSpan> trees;

    @JsonProperty("report_trees")
    public List<TraceTreeSpan> reportTrees;

    @JsonProperty("reports")
    public List<TraceSpanReport> reports;

}
