package proxy

import (
	"net/http"
	"net/http/httputil"

	"dflipse.nl/ds-fit/proxy/tracing"
	"dflipse.nl/ds-fit/shared/faultload"
	"dflipse.nl/ds-fit/shared/trace"
)

type ProxyState struct {
	InjectedFault      *faultload.Fault
	ConcurrentFaults   []*faultload.FaultUid
	Proxy              *httputil.ReverseProxy
	ResponseWriter     *ResponseCapture
	Request            *http.Request
	ReponseOverwritten bool
	DurationMs         float64
}

func (s ProxyState) asReport(metadata tracing.RequestMetadata, hashBody bool) trace.TraceReport {
	response := s.ResponseWriter.GetResponseData(hashBody)
	response.DurationMs = s.DurationMs

	return trace.TraceReport{
		TraceId:       metadata.TraceId,
		SpanId:        metadata.SpanId,
		FaultUid:      *metadata.FaultUid,
		IsInitial:     metadata.IsInitial,
		InjectedFault: s.InjectedFault,
		Response:      &response,
		ConcurrentTo:  s.ConcurrentFaults,
	}
}
