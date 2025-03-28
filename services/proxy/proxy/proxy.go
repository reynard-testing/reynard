package proxy

import (
	"crypto/tls"
	"log"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"

	"dflipse.nl/fit-proxy/config"
	"dflipse.nl/fit-proxy/control"
	"dflipse.nl/fit-proxy/tracing"
	"golang.org/x/net/http2"
	"golang.org/x/net/http2/h2c"
)

var destination string

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
		traceparent := r.Header.Get("traceparent")
		parent := tracing.ParseTraceParent(traceparent)

		// If no traceparent is found, forward the request without inspection
		if parent == nil {
			proxy.ServeHTTP(w, r)
			return
		}

		// Determine if registered as an interesting trace
		faults, ok := control.RegisteredFaults.Get(parent.TraceID)
		if !ok {
			// Forward the request to the target server
			log.Printf("No faults registered for trace ID: %s\n", parent.TraceID)
			proxy.ServeHTTP(w, r)
			return
		}

		// Get and parse the "tracestate" header
		tracestate := r.Header.Get("tracestate")
		state := tracing.ParseTraceState(tracestate)

		// only forward the request if the "fit" flag is set in the tracestate
		shouldInspect := state.GetWithDefault("fit", "0") == "1"
		if !shouldInspect {
			proxy.ServeHTTP(w, r)
			return
		}

		log.Printf("Received injectable request: %s %s\n", r.Method, r.URL)
		log.Printf("Traceparent: %+v\n", parent)
		log.Printf("Tracestate: %+v\n", state)

		// determine the span ID for the current request
		// and report the link to the parent span
		log.Printf("Faults registered for this trace: %s\n", faults)
		shouldMaskPayload := state.GetWithDefault("mask", "0") == "1"
		if shouldMaskPayload {
			log.Printf("Payload masking enabled.\n")
		}

		// determine if this is the initial request
		// and update the tracestate accordingly
		isInitial := state.GetWithDefault("init", "0") == "1"
		if isInitial {
			log.Printf("Initial request.\n")
			state.Delete("init")
			r.Header.Set("tracestate", state.String())
		}

		faultUid := tracing.FaultUidFromRequest(r, destination, shouldMaskPayload, isInitial)
		traceId := parent.TraceID
		log.Printf("Determined Fault UID: %s\n", faultUid.String())
		tracing.TrackFault(traceId, &faultUid)

		var metadata tracing.RequestMetadata = tracing.RequestMetadata{
			TraceId:   traceId,
			SpanId:    parent.ParentID,
			FaultUid:  faultUid,
			IsInitial: isInitial,
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
			ReponseOverwritten: false,
			Complete:           false,
			ConcurrentFaults:   nil,
		}

		// Start reporting the span for the request
		shouldHashBody := state.GetWithDefault("hashbody", "0") == "1"
		if shouldHashBody {
			log.Printf("Body hashing enabled.\n")
		}

		shouldLogHeader := state.GetWithDefault("headerlog", "0") == "1"
		if shouldLogHeader {
			log.Printf("Header logging enabled.\n")
			log.Printf("Headers: %s\n", r.Header)
		}

		tracing.ReportSpanUID(proxyState.asReport(metadata, shouldHashBody))

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

		proxyState.ConcurrentFaults = tracing.GetTrackedAndClear(traceId, &faultUid)
		tracing.ReportSpanUID(proxyState.asReport(metadata, shouldHashBody))
	})
}
