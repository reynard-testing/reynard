package trace

import "dflipse.nl/ds-fit/shared/faultload"

type ResponseData struct {
	Status     int     `json:"status"`
	Body       string  `json:"body"`
	DurationMs float64 `json:"duration_ms"`
}

type TraceReport struct {
	TraceId       faultload.TraceID     `json:"trace_id"`
	SpanId        faultload.SpanID      `json:"span_id"`
	FaultUid      faultload.FaultUid    `json:"uid"`
	IsInitial     bool                  `json:"is_initial"`
	InjectedFault *faultload.Fault      `json:"injected_fault"`
	Response      *ResponseData         `json:"response"`
	ConcurrentTo  []*faultload.FaultUid `json:"concurrent_to"`
}
