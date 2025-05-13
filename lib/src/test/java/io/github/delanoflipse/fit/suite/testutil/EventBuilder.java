package io.github.delanoflipse.fit.suite.testutil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultInjectionPoint;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.faultload.Faultload;
import io.github.delanoflipse.fit.suite.faultload.PartialFaultInjectionPoint;
import io.github.delanoflipse.fit.suite.faultload.modes.ErrorFault;
import io.github.delanoflipse.fit.suite.faultload.modes.FailureMode;
import io.github.delanoflipse.fit.suite.strategy.TrackedFaultload;
import io.github.delanoflipse.fit.suite.strategy.util.Lists;
import io.github.delanoflipse.fit.suite.strategy.util.TraceAnalysis;
import io.github.delanoflipse.fit.suite.trace.tree.TraceReport;
import io.github.delanoflipse.fit.suite.trace.tree.TraceResponse;

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

  public EventBuilder withPoint(String service, String signature) {
    return withPoint(service, signature, Map.of(), 0);
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
    if (report.faultUid == null) {
      report.faultUid = new FaultUid(getStack());
    }
    return report.faultUid;
  }

  public TraceReport build() {
    report.faultUid = uid();
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