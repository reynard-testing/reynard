package proxy

import (
	"net/http"
	"net/http/httputil"

	"dflipse.nl/fit-proxy/faultload"
	"dflipse.nl/fit-proxy/tracing"
)

type ProxyState struct {
	InjectedFault      *faultload.Fault
	Proxy              *httputil.ReverseProxy
	ResponseWriter     *ResponseCapture
	Request            *http.Request
	ReponseOverwritten bool
	Complete           bool
}

func (s ProxyState) asReport(metadata tracing.RequestMetadata) tracing.RequestReport {
	var response *tracing.ResponseData = nil

	if s.Complete {
		responseData := s.ResponseWriter.GetResponseData()
		response = &responseData
	}

	return tracing.RequestReport{
		SpanId:        metadata.SpanId,
		FaultUid:      metadata.FaultUid,
		InjectedFault: s.InjectedFault,
		Response:      response,
	}
}
