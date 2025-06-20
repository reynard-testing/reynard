package trace

import "go.reynard.dev/instrumentation/shared/faultload"

type ResponseData struct {
	Status             int     `json:"status"`
	Body               string  `json:"body"`
	DurationMs         float64 `json:"duration_ms"`
	OverheadDurationMs float64 `json:"overhead_duration_ms"`
}

type TraceReport struct {
	TraceId       faultload.TraceID     `json:"trace_id"`
	SpanId        faultload.SpanID      `json:"span_id"`
	FaultUid      faultload.FaultUid    `json:"uid"`
	IsInitial     bool                  `json:"is_initial"`
	Protocol      string                `json:"protocol"`
	InjectedFault *faultload.Fault      `json:"injected_fault"`
	Response      *ResponseData         `json:"response"`
	ConcurrentTo  []*faultload.FaultUid `json:"concurrent_to"`
}

func (tr *TraceReport) Matches(o *TraceReport) bool {
	if tr.TraceId != o.TraceId {
		return false
	}
	if tr.SpanId != o.SpanId {
		return false
	}

	return true
}
