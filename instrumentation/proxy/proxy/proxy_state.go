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
	Proxy              *httputil.ReverseProxy
	ResponseWriter     *ResponseCapture
	Request            *http.Request
	ReponseOverwritten bool
}

type ProxyFlags struct {
	IsTarget        bool
	IsInitial       bool
	MaskPayload     bool
	HashBody        bool
	LogHeaders      bool
	UsePredecessors bool
}

func (s ProxyState) asReport(metadata tracing.RequestMetadata, hashBody bool) trace.TraceReport {
	response := s.ResponseWriter.GetResponseData(hashBody)
	response.DurationMs = metadata.DurationMs
	response.OverheadDurationMs = metadata.OverheadDurationMs

	return trace.TraceReport{
		TraceId:       metadata.TraceId,
		SpanId:        metadata.SpanId,
		FaultUid:      *metadata.FaultUid,
		IsInitial:     metadata.IsInitial,
		Protocol:      metadata.Protocol,
		InjectedFault: s.InjectedFault,
		Response:      &response,
		ConcurrentTo:  metadata.ConcurrentFaults,
	}
}
