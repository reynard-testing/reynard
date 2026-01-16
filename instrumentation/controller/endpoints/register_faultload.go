package endpoints

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"go.reynard.dev/instrumentation/controller/store"
	"go.reynard.dev/instrumentation/shared/faultload"
)

func RegisterFaultload(w http.ResponseWriter, r *http.Request) {
	// Parse the request body to get the Faultload
	faultload, err := faultload.ParseFaultloadRequest(r)
	if err != nil {
		http.Error(w, "Failed to parse request", http.StatusBadRequest)
		return
	}

	store.TraceFaults.Register(faultload.TraceId, faultload.Faults)

	slog.Info("Registered faultload", "size", len(faultload.Faults), "traceId", faultload.TraceId)

	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}

type UnregisterFaultloadRequest struct {
	TraceId faultload.TraceID `json:"trace_id"`
}

func UnregisterFaultload(w http.ResponseWriter, r *http.Request) {
	// Parse the request body to get the Faultload
	var requestData UnregisterFaultloadRequest
	err := json.NewDecoder(r.Body).Decode(&requestData)
	if err != nil {
		http.Error(w, "Failed to parse request", http.StatusBadRequest)
		return
	}

	store.TraceFaults.Remove(requestData.TraceId)
	store.InvocationCounter.Clear(requestData.TraceId)

	slog.Info("Unregistered faults", "traceId", requestData.TraceId)

	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}
