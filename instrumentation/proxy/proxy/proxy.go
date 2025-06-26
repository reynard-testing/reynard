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
		handleRequest(w, r, proxy)
	})
}

// ensureNonCanonicalForm ensures that the traceparent and tracestate headers are in non-canonical form (lowercase)
func getTracingHeaders(r *http.Request) (string, string) {
	traceparent := r.Header.Get(OTEL_PARENT_HEADER)
	if traceparent == "" {
		// If traceparent is not set, return empty strings
		return "", ""
	}

	// Ensure non-canonical form is kept (lowercase)
	r.Header.Del(OTEL_PARENT_HEADER)
	r.Header[OTEL_PARENT_HEADER] = []string{traceparent}

	tracestate := r.Header.Get(OTEL_STATE_HEADER)
	if tracestate == "" {
		// If tracestate is not set, return empty strings
		return traceparent, ""
	}

	// Ensure non-canonical form is kept (lowercase)
	r.Header.Del(OTEL_STATE_HEADER)
	r.Header[OTEL_STATE_HEADER] = []string{tracestate}

	return traceparent, tracestate
}

func getFlagsFromState(state *tracing.TraceStateData) ProxyFlags {
	if state == nil {
		return ProxyFlags{
			IsTarget:        false,
			IsInitial:       false,
			MaskPayload:     false,
			HashBody:        false,
			LogHeaders:      false,
			UsePredecessors: false,
		}
	}
	// Extract flags from the tracestate
	isInitial := state.GetWithDefault(FIT_IS_INITIAL_KEY, "0") == "1"
	maskPayload := state.GetWithDefault(FIT_MASK_PAYLOAD_FLAG, "0") == "1"
	hashBody := state.GetWithDefault(FIT_HASH_BODY_FLAG, "0") == "1"
	logHeaders := state.GetWithDefault(FIT_HEADER_LOGGING_FLAG, "0") == "1"
	usePredecessors := state.GetWithDefault(FIT_USE_PREDECESSORS, "0") == "1"
	IsTarget := state.GetWithDefault(FIT_FLAG, "0") == "1"

	return ProxyFlags{
		IsTarget:        IsTarget,
		IsInitial:       isInitial,
		MaskPayload:     maskPayload,
		HashBody:        hashBody,
		LogHeaders:      logHeaders,
		UsePredecessors: usePredecessors,
	}
}

func updateState(r *http.Request, state *tracing.TraceStateData) {
	// Update the tracestate header in the request
	if state != nil {
		r.Header[OTEL_STATE_HEADER] = []string{state.String()}
	}
}

func updateTraceParent(r *http.Request, traceParent *tracing.TraceParentData) {
	// Update the traceparent header in the request
	if traceParent != nil {
		r.Header[OTEL_PARENT_HEADER] = []string{traceParent.String()}
	}
}

func handleRequest(w http.ResponseWriter, r *http.Request, proxy *httputil.ReverseProxy) {
	fullStart := time.Now()

	// Determine if there are global faults registered
	globalFaults, hasGlobal := control.RegisteredFaults.GetGlobal()
	if hasGlobal {
		slog.Debug("Global faults registered", "faults", globalFaults)
		// TODO: handle global faults
		// Challenge: We can only build a partial fault uid, and deviate quite a bit from the normal control flow
		// ... so, do we really want to do this?
	}

	// Get and parse the "traceparentHeader" headers
	traceparentHeader, tracestateHeader := getTracingHeaders(r)
	traceParentSpan := tracing.ParseTraceParent(traceparentHeader)

	// If no traceparent is found, forward the request without inspection
	if traceParentSpan == nil {
		proxy.ServeHTTP(w, r)
		return
	}

	// Get the trace ID from the tracing metadata
	traceId := traceParentSpan.TraceID

	// Determine if registered as an interesting trace
	registeredFaults, ofInterest := control.RegisteredFaults.Get(traceId)
	if !ofInterest {
		// Forward the request to the target server
		slog.Debug("Trace ID is not of interest", "traceId", traceId)
		proxy.ServeHTTP(w, r)
		return
	}

	// Get and parse the "tracestate" header
	state := tracing.ParseTraceState(tracestateHeader)
	flags := getFlagsFromState(state)

	// Only consider the request if it is marked as a target
	if !flags.IsTarget {
		proxy.ServeHTTP(w, r)
		return
	}

	// TODO (optional): export to collector, so that jeager understands whats going on
	// Generate a new span ID for the current request
	traceSpan := traceParentSpan.GenerateChildSpan()
	updateTraceParent(r, traceSpan)

	// determine if this is the initial request
	// and update the tracestate accordingly
	if flags.IsInitial {
		slog.Debug("Is initial request")
		state.Delete(FIT_IS_INITIAL_KEY)
		updateState(r, state)
	}

	// Get, and update causal parent in the state
	causalParentId := faultload.SpanID(state.GetWithDefault(FIT_PARENT_KEY, "0"))
	state.Set(FIT_PARENT_KEY, string(traceSpan.ParentID))
	updateState(r, state)

	// Keep track of metadata for the request (for reporting)
	var metadata tracing.RequestMetadata = tracing.RequestMetadata{
		TraceId:            traceId,
		ReportParentId:     causalParentId,
		ParentId:           traceParentSpan.ParentID,
		SpanId:             traceSpan.ParentID,
		IsInitial:          flags.IsInitial,
		Protocol:           util.GetProtocol(r),
		FaultUid:           nil,
		ConcurrentFaults:   nil,
		DurationMs:         0,
		OverheadDurationMs: 0,
	}

	// Log the request
	slog.Debug("Received injectable request", "method", r.Method, "url", r.URL)
	if flags.LogHeaders {
		slog.Info("Logging headers", "headers", r.Header)
	}
	slog.Debug("Traceparent", "traceparent", traceParentSpan)
	slog.Debug("New span id", "spanId", traceSpan.ParentID)
	slog.Debug("Tracestate", "tracestate", state)
	slog.Debug("Flags", "flags", flags)
	slog.Debug("Faults registered for this trace", "faults", registeredFaults)

	// Determine partial point from the request (the parts of the UID that the proxy can determine)
	partialPoint := faultload.PartialPointFromRequest(r, destination, flags.MaskPayload)
	slog.Debug("Partial point", "partialPoint", partialPoint)

	// Determine the full fault UID
	faultUid := tracing.GetUid(metadata, partialPoint, flags.UsePredecessors)
	metadata.FaultUid = &faultUid
	slog.Debug("Determined ID", "faultUid", faultUid)

	// Start tracking the fault (for detecting concurrent in-flight events)
	tracing.TrackFault(traceId, &faultUid)

	// Only directly forward the response if predecessors are not used
	// Because for predecessors we want to ensure we have reports on all previous spans
	directlyForward := !flags.UsePredecessors
	capture := NewResponseCapture(w, directlyForward)

	var proxyState ProxyState = ProxyState{
		Request:            r,
		Proxy:              proxy,
		ResponseWriter:     capture,
		InjectedFault:      nil,
		ReponseOverwritten: false,
	}

	// Start reporting the span for the request

	for _, fault := range registeredFaults {
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
		metadata.DurationMs = time.Since(requestStart).Seconds() * 1000
		slog.Debug("Response forwarded")
	}

	metadata.ConcurrentFaults = tracing.GetTrackedAndClear(traceId, &faultUid)
	metadata.OverheadDurationMs = time.Since(fullStart).Seconds()*1000 - metadata.DurationMs
	tracing.ReportSpanUID(proxyState.asReport(metadata, flags.HashBody))

	if !capture.DirectlyForward {
		err := capture.Flush(flags.LogHeaders)
		if err != nil {
			slog.Error("Error flushing response", "error", err)
		}
	}

	if len(metadata.ConcurrentFaults) > 0 {
		slog.Debug("Concurrent faults", "faults", metadata.ConcurrentFaults)
	}
}
