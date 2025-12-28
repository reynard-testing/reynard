package dev.reynard.junit.instrumentation.controller;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.reynard.junit.instrumentation.trace.tree.TraceReport;

@JsonDeserialize
public class ControllerResponse {

    @JsonProperty("reports")
    public List<TraceReport> reports;

}
