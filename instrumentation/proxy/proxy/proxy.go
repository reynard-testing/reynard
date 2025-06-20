package proxy

import (
	"crypto/tls"
	"log"
	"log/slog"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"time"

	"go.reynard.dev/instrumentation/proxy/config"
	"go.reynard.dev/instrumentation/proxy/control"
	"go.reynard.dev/instrumentation/proxy/tracing"
	"go.reynard.dev/instrumentation/shared/faultload"
	"go.reynard.dev/instrumentation/shared/util"
	"golang.org/x/net/http2"
	"golang.org/x/net/http2/h2c"
)

var destination string

const (
	FIT_PARENT_KEY          = "fit-parent"
	FIT_IS_INITIAL_KEY      = "init"
	FIT_FLAG                = "fit"
	FIT_MASK_PAYLOAD_FLAG   = "maskpyld"
	FIT_HASH_BODY_FLAG      = "hashbody"
	FIT_USE_PREDECESSORS    = "usepred"
	FIT_HEADER_LOGGING_FLAG = "hdrlog"

	OTEL_PARENT_HEADER = "traceparent"
	OTEL_STATE_HEADER  = "tracestate"
)

func StartProxy(config config.ProxyConfig) {
	slog.Info("Starting proxy server", "host", config.Host, "target", config.Target)
	slog.Info("HTTP/2", "enabled", config.UseHttp2)
	slog.Info("Destination", "host", config.Destination)

	// Start an HTTP/2 server with a custom reverse proxy handler
	var httpServer *http.Server
	var handler http.Handler = proxyHandler(config.Target, config.UseHttp2)

	if config.UseHttp2 {
		handler = h2c.NewHandler(handler, &http2.Server{})
	}

	httpServer = &http.Server{
		Addr:        config.Host,
		Handler:     handler,
		IdleTimeout: 120 * time.Second,
	}

	destination = config.Destination
	err := httpServer.ListenAndServe()
	// err := httpServer.ListenAndServeTLS("cert.pem", "key.pem") // Requires SSL certificates for HTTP/2

	if err != nil {
		log.Fatalf("Error starting proxy server: %v\n", err)
	}
}

// Proxy handler that inspects and forwards HTTP requests and responses
func proxyHandler(targetHost string, useHttp2 bool) http.Handler {
	// Parse the target URL
	targetURL, err := url.Parse(targetHost)
	if err != nil {
		log.Fatalf("Failed to parse target host: %v\n", err)
	}

	// Create the reverse proxy
	proxy := httputil.NewSingleHostReverseProxy(targetURL)

	// Fix issue with wrongly inferred content type if none set
	proxy.ModifyResponse = func(resp *http.Response) error {
		if resp.Header.Get("content-type") == "" {
			resp.Header.Set("content-type", "application/octet-stream")
		}

		return nil
	}

	if useHttp2 {
		proxy.Transport = &http2.Transport{
			AllowHTTP: true,
			DialTLS: func(network, addr string, cfg *tls.Config) (net.Conn, error) {
				return net.Dial(network, addr)
			},
		}
	} else {
		// Start with defaults
		// customTransport.ExpectContinueTimeout = 1 * time.Second
		proxy.Transport = util.GetDefaultTransport()
	}

	// Wrap the proxy with a custom handler to inspect requests and responses
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fullStart := time.Now()
		// Inspect request before forwarding

		// Get and parse the "traceparent" headers
		traceparent := r.Header.Get(OTEL_PARENT_HEADER)
		parentSpan := tracing.ParseTraceParent(traceparent)

		// If no traceparent is found, forward the request without inspection
		if parentSpan == nil {
			proxy.ServeHTTP(w, r)
			return
		}

		traceId := parentSpan.TraceID

		// Get and parse the "tracestate" header
		tracestate := r.Header.Get(OTEL_STATE_HEADER)
		state := tracing.ParseTraceState(tracestate)

		// Ensure non-conical form is kept
		r.Header.Del(OTEL_PARENT_HEADER)
		r.Header[OTEL_PARENT_HEADER] = []string{traceparent}
		r.Header.Del(OTEL_STATE_HEADER)
		r.Header[OTEL_STATE_HEADER] = []string{tracestate}

		// Determine if registered as an interesting trace
		faults, ok := control.RegisteredFaults.Get(traceId)
		if !ok {
			// Forward the request to the target server
			slog.Debug("No faults registered for trace ID", "traceId", traceId)
			proxy.ServeHTTP(w, r)
			return
		}

		// TODO (optional): export to collector, so that jeager understands whats going on
		currentSpan := parentSpan.GenerateNew()
		r.Header[OTEL_PARENT_HEADER] = []string{currentSpan.String()}

		// only forward the request if the "fit" flag is set in the tracestate
		shouldInspect := state.GetWithDefault(FIT_FLAG, "0") == "1"
		if !shouldInspect {
			proxy.ServeHTTP(w, r)
			return
		}

		slog.Debug("Received injectable request", "method", r.Method, "url", r.URL)
		slog.Debug("Traceparent", "traceparent", parentSpan)
		slog.Debug("New span id", "spanId", currentSpan.ParentID)
		slog.Debug("Tracestate", "tracestate", state)

		// determine the span ID for the current request
		// and report the link to the parent span
		slog.Debug("Faults registered for this trace", "faults", faults)
		shouldMaskPayload := state.GetWithDefault(FIT_MASK_PAYLOAD_FLAG, "0") == "1"
		slog.Debug("Mask payload", "enabled", shouldMaskPayload)

		// determine if this is the initial request
		// and update the tracestate accordingly
		isInitial := state.GetWithDefault(FIT_IS_INITIAL_KEY, "0") == "1"
		if isInitial {
			slog.Debug("Is initial request")
			state.Delete(FIT_IS_INITIAL_KEY)
			r.Header[OTEL_STATE_HEADER] = []string{state.String()}
		}

		// -- Determine FID --
		reportParentId := faultload.SpanID(state.GetWithDefault(FIT_PARENT_KEY, "0"))
		protocol := util.GetProtocol(r)
		slog.Debug("Protocol", "protocol", protocol)

		var metadata tracing.RequestMetadata = tracing.RequestMetadata{
			TraceId:        traceId,
			ReportParentId: reportParentId,
			ParentId:       parentSpan.ParentID,
			SpanId:         currentSpan.ParentID,
			IsInitial:      isInitial,
			Protocol:       protocol,
			FaultUid:       nil,
		}

		partialPoint := faultload.PartialPointFromRequest(r, destination, shouldMaskPayload)
		slog.Debug("Partial point", "partialPoint", partialPoint)

		// determine if call stacks should be used
		shouldUsePredecessors := state.GetWithDefault(FIT_USE_PREDECESSORS, "0") == "1"
		slog.Debug("Report parent ID", "reportParentId", reportParentId)

		faultUid := tracing.GetUid(metadata, partialPoint, shouldUsePredecessors)
		metadata.FaultUid = &faultUid
		slog.Debug("Determined ID", "faultUid", faultUid)
		tracing.TrackFault(traceId, &faultUid)
		// --

		state.Set(FIT_PARENT_KEY, string(currentSpan.ParentID))
		r.Header[OTEL_STATE_HEADER] = []string{state.String()}

		// Only directly forward the response if call stacks are not used
		// Because for call stacks we want to ensure we have reports on all previous spans
		directlyForward := !shouldUsePredecessors
		capture := NewResponseCapture(w, directlyForward)

		var proxyState ProxyState = ProxyState{
			InjectedFault:      nil,
			Proxy:              proxy,
			ResponseWriter:     capture,
			Request:            r,
			DurationMs:         0,
			OverheadDurationMs: 0,
			ReponseOverwritten: false,
			ConcurrentFaults:   nil,
		}

		// Start reporting the span for the request
		shouldHashBody := state.GetWithDefault(FIT_HASH_BODY_FLAG, "0") == "1"
		slog.Debug("Hash body", "enabled", shouldHashBody)

		shouldLogHeader := state.GetWithDefault(FIT_HEADER_LOGGING_FLAG, "0") == "1"
		if shouldLogHeader {
			slog.Info("Logging headers", "headers", r.Header)
		}

		for _, fault := range faults {
			if fault.Uid.Matches(faultUid) {
				Perform(fault, &proxyState)
				break
			}
		}

		if proxyState.InjectedFault != nil {
			slog.Debug("Fault injected", "fault", proxyState.InjectedFault)
		} else {
			slog.Debug("No fault injected")
		}

		// Forward the request to the target server
		if !proxyState.ReponseOverwritten {
			requestStart := time.Now()
			proxy.ServeHTTP(capture, r)
			proxyState.DurationMs = time.Since(requestStart).Seconds() * 1000
			slog.Debug("Response forwarded")
		}

		proxyState.ConcurrentFaults = tracing.GetTrackedAndClear(traceId, &faultUid)
		proxyState.OverheadDurationMs = time.Since(fullStart).Seconds()*1000 - proxyState.DurationMs
		tracing.ReportSpanUID(proxyState.asReport(metadata, shouldHashBody))

		if !capture.DirectlyForward {
			err := capture.Flush(shouldLogHeader)
			if err != nil {
				slog.Error("Error flushing response", "error", err)
			}
		}

		if len(proxyState.ConcurrentFaults) > 0 {
			slog.Debug("Concurrent faults", "faults", proxyState.ConcurrentFaults)
		}
	})
}
