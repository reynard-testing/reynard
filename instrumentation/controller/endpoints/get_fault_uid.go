package endpoints

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"go.reynard.dev/instrumentation/controller/store"
	"go.reynard.dev/instrumentation/shared/faultload"
	"go.reynard.dev/instrumentation/shared/trace"
)

type UidRequest struct {
	TraceId        faultload.TraceID               `json:"trace_id"`
	SpanId         faultload.SpanID                `json:"span_id"`
	ReportParentId faultload.SpanID                `json:"parent_span_id"`
	PartialPoint   faultload.PartialInjectionPoint `json:"partial_point"`
	IsInitial      bool                            `json:"is_initial"`
	IncludeEvents  bool                            `json:"include_events"`
}

type UidResponse struct {
	Uid faultload.FaultUid `json:"uid"`
}

func getCompletedEvents(parentEvent *trace.TraceReport) *faultload.InjectionPointCallStack {
	reports := store.Reports.GetByTraceId(parentEvent.TraceId)
	completed := faultload.InjectionPointCallStack{}

	for _, report := range reports {
		// Ignore the parent event itself and any incomplete reports.
		if report.SpanId == parentEvent.SpanId || report.Response == nil {
			continue
		}

		// Check if the report's stack matches the parent event's stack.
		reportParent := report.FaultUid.Parent()
		if !reportParent.Matches(parentEvent.FaultUid) {
			continue
		}

		point := report.FaultUid.Point()
		key := point.AsPartial().String()

		currentCount, exists := completed[key]
		if !exists || currentCount < point.Count {
			completed[key] = point.Count
		}
	}

	return &completed
}

func determineUid(data UidRequest) *faultload.FaultUid {
	if data.IsInitial {
		uid := faultload.BuildFaultUid(faultload.FaultUid{}, data.PartialPoint, nil, 0)
		return &uid
	}

	parentReport := store.Reports.GetByTraceAndSpanId(data.TraceId, data.ReportParentId)
	if parentReport == nil {
		slog.Error("Parent report not found", "traceId", data.TraceId, "parentSpanId", data.ReportParentId)
		return nil
	}

	var callStack *faultload.InjectionPointCallStack
	if data.IncludeEvents {
		callStack = getCompletedEvents(parentReport)
		// do not include the current span in the call stack
		callStack.Del(data.PartialPoint)
	}

	invocationCount := store.InvocationCounter.GetCount(data.TraceId, parentReport.FaultUid, data.PartialPoint, callStack)

	uid := faultload.BuildFaultUid(parentReport.FaultUid, data.PartialPoint, callStack, invocationCount)
	return &uid

}

func GetFaultUid(w http.ResponseWriter, r *http.Request) {
	var data UidRequest
	if err := json.NewDecoder(r.Body).Decode(&data); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}

	slog.Debug("Received uid request", "traceId", data.TraceId, "parentSpanId", data.ReportParentId, "spanId", data.SpanId, "partialPoint", data.PartialPoint, "isInitial", data.IsInitial)

	faultUid := determineUid(data)
	if faultUid == nil {
		http.Error(w, "Failed to determine uid", http.StatusInternalServerError)
		return
	}

	slog.Debug("Determined uid", "uid", faultUid)

	traceReport := trace.TraceReport{
		TraceId:       data.TraceId,
		SpanId:        data.SpanId,
		FaultUid:      *faultUid,
		IsInitial:     data.IsInitial,
		InjectedFault: nil,
		Response:      nil,
		ConcurrentTo:  nil,
	}

	store.Reports.Add(traceReport)

	response := UidResponse{
		Uid: *faultUid,
	}

	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(response); err != nil {
		http.Error(w, "Failed to encode response", http.StatusInternalServerError)
	}
}
