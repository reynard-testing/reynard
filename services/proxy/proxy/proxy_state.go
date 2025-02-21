package proxy

import (
	"net/http"
	"net/http/httputil"

	"dflipse.nl/fit-proxy/faultload"
	"dflipse.nl/fit-proxy/tracing"
)

type ProxyState struct {
	AppliedFault       *faultload.Fault
	Proxy              *httputil.ReverseProxy
	ResponseWriter     *ResponseCapture
	Request            *http.Request
	FaultInjected      bool
	ReponseOverwritten bool
}

func (s ProxyState) asReport(metadata tracing.RequestMetadata) tracing.RequestReport {
	return tracing.RequestReport{
		SpanID:        metadata.SpanID,
		SpanUID:       metadata.SpanUID,
		FaultInjected: s.FaultInjected,
		Response:      s.ResponseWriter.GetResponseData(),
	}
}
