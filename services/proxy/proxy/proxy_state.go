package proxy

import (
	"net/http"
	"net/http/httputil"

	"dflipse.nl/ds-fit/proxy/tracing"
	"dflipse.nl/ds-fit/shared/faultload"
)

type ProxyState struct {
	InjectedFault      *faultload.Fault
	ConcurrentFaults   []*faultload.FaultUid
	Proxy              *httputil.ReverseProxy
	ResponseWriter     *ResponseCapture
	Request            *http.Request
	ReponseOverwritten bool
	Complete           bool
	DurationMs         float64
}

func (s ProxyState) asReport(metadata tracing.RequestMetadata, hashBody bool) tracing.RequestReport {
	var response *tracing.ResponseData = nil

	if s.Complete {
		responseData := s.ResponseWriter.GetResponseData(hashBody)
		response = &responseData
		response.DurationMs = s.DurationMs
	}

	return tracing.RequestReport{
		TraceId:       metadata.TraceId,
		SpanId:        metadata.SpanId,
		FaultUid:      metadata.FaultUid,
		IsInitial:     metadata.IsInitial,
		InjectedFault: s.InjectedFault,
		Response:      response,
		ConcurrentTo:  s.ConcurrentFaults,
	}
}
