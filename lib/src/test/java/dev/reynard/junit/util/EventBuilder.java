package dev.reynard.junit.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.reynard.junit.faultload.Behaviour;
import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultInjectionPoint;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.faultload.Faultload;
import dev.reynard.junit.faultload.modes.ErrorFault;
import dev.reynard.junit.faultload.modes.FailureMode;
import dev.reynard.junit.instrumentation.trace.tree.TraceReport;
import dev.reynard.junit.instrumentation.trace.tree.TraceResponse;
import dev.reynard.junit.strategy.TrackedFaultload;
import dev.reynard.junit.strategy.util.Lists;
import dev.reynard.junit.strategy.util.TraceAnalysis;

public class EventBuilder {
  TraceReport report = new TraceReport();
  private static int spanCounter = 0;
  List<EventBuilder> children = new ArrayList<>();
  EventBuilder parent = null;
  FaultInjectionPoint point = null;

  private String newSpanId() {
    spanCounter++;
    return String.valueOf(spanCounter);
  }

  public EventBuilder() {
    this(null, "");
  }

  public EventBuilder(EventBuilder parent) {
    this(parent, parent.report.traceId);
  }

  public EventBuilder(String name) {
    this(null, "");
    withPoint(name, name + "1");
  }

  public EventBuilder(EventBuilder parent, String traceId) {
    report.isInitial = parent == null;
    report.spanId = newSpanId();
    report.traceId = traceId;
    report.response = new TraceResponse();
    report.response.durationMs = 1f;
    report.response.status = 200;
    report.response.body = "OK";
    this.parent = parent;
  }

  public EventBuilder withPoint(String service, String signature, Map<String, Integer> cs,
      int count) {
    point = new FaultInjectionPoint(service, signature, "", cs, count);
    return this;
  }

  public EventBuilder withPoint(String service, String signature, int count) {
    point = new FaultInjectionPoint(service, signature, "", Map.of(), count);
    return this;
  }

  public EventBuilder withPoint(String service, String signature, Map<String, Integer> cs) {
    return withPoint(service, signature, cs, 0);
  }

  public EventBuilder withPoint(String service, Map<String, Integer> cs, int count) {
    return withPoint(service, service, cs, count);
  }

  public EventBuilder withPoint(String service, String signature) {
    return withPoint(service, signature, Map.of(), 0);
  }

  public EventBuilder withPoint(String service, int count) {
    return withPoint(service, service, Map.of(), count);
  }

  public EventBuilder withPoint(String service) {
    return withPoint(service, service, Map.of(), 0);
  }

  public EventBuilder withResponse(int status, String body) {
    report.response.status = status;
    report.response.body = body;
    return this;
  }

  public EventBuilder createChild() {
    var builder = new EventBuilder(this);
    children.add(builder);
    return builder;
  }

  public EventBuilder createChild(String name) {
    return createChild()
        .withPoint(name, name + "1");
  }

  public EventBuilder findService(String service) {
    if (point != null && point.destination().equals(service)) {
      return this;
    }

    for (var child : children) {
      var builder = child.findService(service);
      if (builder != null) {
        return builder;
      }
    }
    return null;
  }

  public Set<Fault> getFaults() {
    Set<Fault> faults = new LinkedHashSet<>();
    if (report.injectedFault != null) {
      faults.add(report.injectedFault);
    }

    for (var child : children) {
      faults.addAll(child.getFaults());
    }
    return faults;
  }

  public EventBuilder withFault(FailureMode mode) {
    Fault fault = new Fault(uid(), mode);
    this.report.injectedFault = fault;

    if (fault.mode().getType().equals(ErrorFault.FAULT_TYPE)) {
      int statusCode = Integer.parseInt(fault.mode().getArgs().get(0));
      withResponse(statusCode, "err");
    }

    return this;
  }

  public List<FaultInjectionPoint> getStack() {
    if (parent == null) {
      return List.of(point);
    }
    return Lists.plus(parent.getStack(), point);
  }

  public Behaviour behaviour() {
    if (report.injectedFault != null) {
      return new Behaviour(report.injectedFault.uid(), report.injectedFault.mode());
    }

    return new Behaviour(uid(), null);
  }

  public FaultUid uid() {
    if (point == null) {
      point = new FaultInjectionPoint("unknown", "unknown", "", Map.of(), 0);
    }
    if (report.injectionPoint == null) {
      report.injectionPoint = new FaultUid(getStack());
    }
    return report.injectionPoint;
  }

  public TraceReport build() {
    report.injectionPoint = uid();
    return report;
  }

  public List<TraceReport> buildAll() {
    List<TraceReport> reports = new ArrayList<>();
    reports.add(build());
    for (var child : children) {
      reports.addAll(child.buildAll());
    }
    return reports;
  }

  public TraceAnalysis buildTrace() {
    return new TraceAnalysis(buildAll());
  }

  public Faultload buildFaultload() {
    return new Faultload(getFaults());
  }

  public TrackedFaultload buildTrackedFaultload() {
    return new TrackedFaultload(buildFaultload());
  }
}