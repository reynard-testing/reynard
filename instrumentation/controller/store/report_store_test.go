package store

import (
	"testing"
	"time"

	"go.reynard.dev/instrumentation/shared/trace"
)

func TestRemoveByTraceIdFreesReportsWithoutDeadlock(t *testing.T) {
	rs := NewReportStore()
	rs.Add(trace.TraceReport{TraceId: "t1", SpanId: "a"})
	rs.Add(trace.TraceReport{TraceId: "t1", SpanId: "b"})
	rs.Add(trace.TraceReport{TraceId: "t2", SpanId: "c"})

	done := make(chan struct{})
	go func() {
		rs.RemoveByTraceId("t1")
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("RemoveByTraceId did not return (deadlock)")
	}

	if rs.HasTraceId("t1") || rs.HasSpanIdForTraceId("t1", "a") || rs.HasSpanIdForTraceId("t1", "b") {
		t.Error("reports of t1 were not removed")
	}
	if len(rs.reports) != 1 || !rs.HasSpanIdForTraceId("t2", "c") {
		t.Error("reports of t2 should be untouched")
	}
}
