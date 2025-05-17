package endpoints

import (
	"encoding/json"
	"log"
	"net/http"

	"dflipse.nl/ds-fit/controller/store"
	"dflipse.nl/ds-fit/shared/faultload"
	"dflipse.nl/ds-fit/shared/trace"
)

type UidRequest struct {
	TraceId        faultload.TraceID `json:"trace_id"`
	ReportParentId faultload.SpanID  `json:"parent_span_id"`
}
type UidResponse struct {
	Stack           []faultload.InjectionPoint        `json:"stack"`
	CompletedEvents faultload.InjectionPointCallStack `json:"completed_events"`
}

func getCompletedEvents(parentEvent *trace.TraceReport) map[string]int {
	reports := store.Reports.GetByTraceId(parentEvent.TraceId)
	completed := make(map[string]int)

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

	return completed
}

func GetFaultUid(w http.ResponseWriter, r *http.Request) {
	var data UidRequest
	if err := json.NewDecoder(r.Body).Decode(&data); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}

	log.Printf("Received request for trace ID: %s, parent span ID: %s", data.TraceId, data.ReportParentId)

	report := store.Reports.GetByTraceAndSpanId(data.TraceId, data.ReportParentId)
	if report == nil {
		http.Error(w, "Report not found", http.StatusNotFound)
		return
	}

	completedEvents := getCompletedEvents(report)

	response := UidResponse{
		Stack:           report.FaultUid.Stack,
		CompletedEvents: completedEvents,
	}

	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(response); err != nil {
		http.Error(w, "Failed to encode response", http.StatusInternalServerError)
	}
}
