package endpoints

import (
	"encoding/json"
	"fmt"
	"log"
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
		log.Printf("Updated reported span %s for trace id %s\n", data.SpanId, data.TraceId)
	} else {
		log.Printf("Added reported span %s for trace id %s\n", data.SpanId, data.TraceId)
	}

	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}
