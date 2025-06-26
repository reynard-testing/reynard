package faultload

import (
	"encoding/json"
	"log/slog"
	"net/http"
)

type SpanID string
type TraceID string

type Faultload struct {
	Faults  []Fault  `json:"faults"`
	TraceId *TraceID `json:"trace_id"`
}

func ParseFaultloadRequest(r *http.Request) (*Faultload, error) {
	var faultload Faultload

	err := json.NewDecoder(r.Body).Decode(&faultload)

	if err != nil {
		slog.Error("Failed to decode request body", "error", err)
		return nil, err
	}

	return &faultload, nil
}
