package endpoints

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"

	"dflipse.nl/ds-fit/controller/store"
	"dflipse.nl/ds-fit/shared/trace"
)

func ReportSpanId(w http.ResponseWriter, r *http.Request) {
	var data trace.TraceReport
	if err := json.NewDecoder(r.Body).Decode(&data); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	if !store.TraceIds.IsRegistered(data.TraceId) {
		fmt.Fprintf(w, "Trace id (%s) not registered anymore for uid %v", data.TraceId, data.FaultUid.String())
		w.WriteHeader(http.StatusNotFound)
		return
	}

	existed := store.Reports.Upsert(data)

	if existed {
		slog.Debug("Updated reported span", "spanId", data.SpanId, "traceId", data.TraceId)
	} else {
		slog.Debug("Added reported span", "spanId", data.SpanId, "traceId", data.TraceId)
	}

	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}
