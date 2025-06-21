package proxy

import (
	"net/http"
	"net/http/httputil"

	"go.reynard.dev/instrumentation/proxy/tracing"
	"go.reynard.dev/instrumentation/shared/faultload"
	"go.reynard.dev/instrumentation/shared/trace"
)

type ProxyState struct {
	InjectedFault      *faultload.Fault
	ConcurrentFaults   []*faultload.FaultUid
	Proxy              *httputil.ReverseProxy
	ResponseWriter     *ResponseCapture
	Request            *http.Request
	ReponseOverwritten bool
	DurationMs         float64
	OverheadDurationMs float64
}

func (s ProxyState) asReport(metadata tracing.RequestMetadata, hashBody bool) trace.TraceReport {
	response := s.ResponseWriter.GetResponseData(hashBody)
	response.DurationMs = s.DurationMs
	response.OverheadDurationMs = s.OverheadDurationMs

	return trace.TraceReport{
		TraceId:       metadata.TraceId,
		SpanId:        metadata.SpanId,
		FaultUid:      *metadata.FaultUid,
		IsInitial:     metadata.IsInitial,
		Protocol:      metadata.Protocol,
		InjectedFault: s.InjectedFault,
		Response:      &response,
		ConcurrentTo:  s.ConcurrentFaults,
	}
}
