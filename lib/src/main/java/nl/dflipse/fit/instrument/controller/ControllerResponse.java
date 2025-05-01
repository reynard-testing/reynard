package io.github.delanoflipse.fit.instrument.controller;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.github.delanoflipse.fit.trace.tree.TraceReport;

@JsonDeserialize
public class ControllerResponse {

    @JsonProperty("reports")
    public List<TraceReport> reports;

}
