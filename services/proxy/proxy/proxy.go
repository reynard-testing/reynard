package proxy

import (
	"crypto/tls"
	"log"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"time"

	"dflipse.nl/fit-proxy/config"
	"dflipse.nl/fit-proxy/control"
	"dflipse.nl/fit-proxy/faultload"
	"dflipse.nl/fit-proxy/tracing"
	"golang.org/x/net/http2"
	"golang.org/x/net/http2/h2c"
)

var destination string

const (
	FIT_PARENT_KEY          = "fit-parent"
	FIT_IS_INITIAL_KEY      = "init"
	FIT_FLAG                = "fit"
	FIT_MASK_PAYLOAD_FLAG   = "mask"
	FIT_HASH_BODY_FLAG      = "hashbody"
	FIT_HEADER_LOGGING_FLAG = "headerlog"

	OTEL_PARENT_HEADER = "traceparent"
	OTEL_STATE_HEADER  = "tracestate"
)

func StartProxy(config config.ProxyConfig) {
	// Set up the proxy host and target

	log.Printf("Reverse proxy for: %s\n", config.Target)
	log.Printf("Destination: %s\n", config.Destination)
	log.Printf("Listening at: %s\n", config.Host)

	// Start an HTTP/2 server with a custom reverse proxy handler
	var httpServer *http.Server
	var handler http.Handler = proxyHandler(config.Target, config.UseHttp2)

	if config.UseHttp2 {
		log.Printf("Using HTTP/2\n")
		handler = h2c.NewHandler(handler, &http2.Server{})
	}

	httpServer = &http.Server{
		Addr:    config.Host,
		Handler: handler,
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
	}

	// Wrap the proxy with a custom handler to inspect requests and responses
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Inspect request before forwarding

		// Get and parse the "traceparent" headers
		traceparent := r.Header.Get(OTEL_PARENT_HEADER)
		parentSpan := tracing.ParseTraceParent(traceparent)

		// If no traceparent is found, forward the request without inspection
		if parentSpan == nil {
			proxy.ServeHTTP(w, r)
			return
		}

		// Determine if registered as an interesting trace
		faults, ok := control.RegisteredFaults.Get(parentSpan.TraceID)
		if !ok {
			// Forward the request to the target server
			log.Printf("No faults registered for trace ID: %s\n", parentSpan.TraceID)
			proxy.ServeHTTP(w, r)
			return
		}

		// Get and parse the "tracestate" header
		tracestate := r.Header.Get(OTEL_STATE_HEADER)
		state := tracing.ParseTraceState(tracestate)
		traceId := parentSpan.TraceID
		// TODO (optional): export to collector, so that jeager understands whats going on
		currentSpan := parentSpan.GenerateNew()
		r.Header.Set(OTEL_PARENT_HEADER, currentSpan.String())

		// only forward the request if the "fit" flag is set in the tracestate
		shouldInspect := state.GetWithDefault(FIT_FLAG, "0") == "1"
		if !shouldInspect {
			proxy.ServeHTTP(w, r)
			return
		}

		log.Printf("Received injectable request: %s %s\n", r.Method, r.URL)
		log.Printf("Traceparent: %+v\n", parentSpan)
		log.Printf("New span id: %+v\n", currentSpan.ParentID)
		log.Printf("Tracestate: %+v\n", state)

		// determine the span ID for the current request
		// and report the link to the parent span
		log.Printf("Faults registered for this trace: %s\n", faults)
		shouldMaskPayload := state.GetWithDefault(FIT_MASK_PAYLOAD_FLAG, "0") == "1"
		if shouldMaskPayload {
			log.Printf("Payload masking enabled.\n")
		}

		// determine if this is the initial request
		// and update the tracestate accordingly
		isInitial := state.GetWithDefault(FIT_IS_INITIAL_KEY, "0") == "1"
		if isInitial {
			log.Printf("Initial request.\n")
			state.Delete(FIT_IS_INITIAL_KEY)
			r.Header.Set(OTEL_STATE_HEADER, state.String())
		}

		// -- Determine FID --
		reportParentId := state.GetWithDefault(FIT_PARENT_KEY, "0")
		log.Printf("Report parent ID: %s\n", reportParentId)
		partialPoint := tracing.PartialPointFromRequest(r, destination, shouldMaskPayload)
		parentStack := tracing.GetUid(tracing.UidRequest{
			TraceId:        traceId,
			ReportParentId: reportParentId,
			IsInitial:      isInitial,
		})
		invocationCount := tracing.GetCountForTrace(traceId, parentStack, partialPoint)
		faultUid := faultload.BuildFaultUid(parentStack, partialPoint, invocationCount)
		// --

		state.Set(FIT_PARENT_KEY, currentSpan.ParentID)
		r.Header.Set(OTEL_STATE_HEADER, state.String())

		var metadata tracing.RequestMetadata = tracing.RequestMetadata{
			TraceId:        traceId,
			ReportParentId: reportParentId,
			ParentId:       parentSpan.ParentID,
			SpanId:         currentSpan.ParentID,
			FaultUid:       faultUid,
			IsInitial:      isInitial,
		}

		capture := &ResponseCapture{
			ResponseWriter: w,
			Status:         http.StatusOK,
		}

		var proxyState ProxyState = ProxyState{
			InjectedFault:      nil,
			Proxy:              proxy,
			ResponseWriter:     capture,
			Request:            r,
			DurationMs:         0,
			ReponseOverwritten: false,
			Complete:           false,
			ConcurrentFaults:   nil,
		}

		// Start reporting the span for the request
		shouldHashBody := state.GetWithDefault(FIT_HASH_BODY_FLAG, "0") == "1"
		if shouldHashBody {
			log.Printf("Body hashing enabled.\n")
		}

		shouldLogHeader := state.GetWithDefault(FIT_HEADER_LOGGING_FLAG, "0") == "1"
		if shouldLogHeader {
			log.Printf("Header logging enabled.\n")
			log.Printf("Headers: %s\n", r.Header)
		}

		log.Printf("Determined Fault UID: %s\n", faultUid.String())
		tracing.ReportSpanUID(proxyState.asReport(metadata, shouldHashBody))
		tracing.TrackFault(traceId, &faultUid)

		startTime := time.Now()
		for _, fault := range faults {
			if fault.Uid.Matches(faultUid) {
				Perform(fault, &proxyState)
				break
			}
		}

		if proxyState.InjectedFault != nil {
			log.Printf("Fault injected: %s\n", proxyState.InjectedFault)
		} else {
			log.Printf("No faults applied.\n")
		}

		// Forward the request to the target server
		if !proxyState.ReponseOverwritten {
			proxy.ServeHTTP(capture, r)
			log.Printf("Forwarding response.\n")
		}

		proxyState.Complete = true
		proxyState.DurationMs = uint64(time.Since(startTime).Milliseconds())
		proxyState.ConcurrentFaults = tracing.GetTrackedAndClear(traceId, &faultUid)

		tracing.ReportSpanUID(proxyState.asReport(metadata, shouldHashBody))

		if len(proxyState.ConcurrentFaults) > 0 {
			log.Printf("Concurrent faults: %s\n", proxyState.ConcurrentFaults)
		}
	})
}
