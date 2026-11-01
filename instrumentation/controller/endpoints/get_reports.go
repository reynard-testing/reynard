package endpoints

import (
	"encoding/json"
	"net/http"

	"go.reynard.dev/instrumentation/controller/store"
	"go.reynard.dev/instrumentation/shared/faultload"
	"go.reynard.dev/instrumentation/shared/trace"
)

type GetReportsByTraceIDResponse struct {
	Reports []trace.TraceReport `json:"reports"`
}

func GetReportsByTraceID(w http.ResponseWriter, r *http.Request) {
	traceID := faultload.TraceID(r.PathValue("trace_id"))

	if !store.TraceFaults.IsTraceRegistered(traceID) {
		http.Error(w, "Trace ID not registered", http.StatusNotFound)
		return
	}

	reports := store.Reports.GetByTraceId(traceID)

	response := GetReportsByTraceIDResponse{Reports: reports}
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(response); err != nil {
		http.Error(w, "Failed to encode response", http.StatusInternalServerError)
	}
}
