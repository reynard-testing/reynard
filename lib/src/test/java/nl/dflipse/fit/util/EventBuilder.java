package nl.dflipse.fit.util;

import java.util.ArrayList;
import java.util.List;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultInjectionPoint;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.FailureMode;
import nl.dflipse.fit.strategy.util.Lists;
import nl.dflipse.fit.strategy.util.TraceAnalysis;
import nl.dflipse.fit.trace.tree.TraceReport;
import nl.dflipse.fit.trace.tree.TraceResponse;

public class EventBuilder {
  TraceReport report = new TraceReport();
  private static int spanCounter = 0;
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

  public EventBuilder(EventBuilder parent, String traceId) {
    report.isInitial = parent == null;
    report.spanId = newSpanId();
    report.traceId = traceId;
    report.response = new TraceResponse();
    report.response.durationMs = 1;
    report.response.status = 200;
    report.response.body = "OK";
    this.parent = parent;
  }

  public EventBuilder withPoint(String service, String signature, int count) {
    point = new FaultInjectionPoint(service, signature, "", count);
    return this;
  }

  public EventBuilder withPoint(String service, String signature) {
    return withPoint(service, signature, 0);
  }

  public EventBuilder withResponse(int status, String body) {
    report.response.status = status;
    report.response.body = body;
    return this;
  }

  public EventBuilder createChild() {
    return new EventBuilder(this);
  }

  public EventBuilder withFault(FailureMode mode) {
    Fault fault = new Fault(report.faultUid, mode);
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
    return Lists.add(parent.getStack(), point);
  }

  public FaultUid getFaultUid() {
    if (point == null) {
      point = new FaultInjectionPoint("unknown", "unknown", "", 0);
    }
    if (report.faultUid == null) {
      report.faultUid = new FaultUid(getStack());
    }
    return report.faultUid;
  }

  public TraceReport build() {
    report.faultUid = getFaultUid();
    return report;
  }

  public static List<TraceReport> buildAll(EventBuilder... events) {
    List<TraceReport> reports = new ArrayList<>();
    for (var event : events) {
      reports.add(event.build());
    }
    return reports;
  }

  public static TraceAnalysis buildTrace(EventBuilder... events) {
    return new TraceAnalysis(buildAll(events));
  }
}