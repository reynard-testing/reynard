package main

import (
	"crypto/tls"
	"fmt"
	"log"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"strings"

	"dflipse.nl/fit-proxy/tracing"
	"golang.org/x/net/http2"
	"golang.org/x/net/http2/h2c"
)

var queryHost string = os.Getenv("COLLECTOR_HOST")

func reportSpanUID(traceparent tracing.TraceParentData, spanUID string) {
	queryUrl := fmt.Sprintf("http://%s/v1/link", queryHost)
	encodedSpanUID := url.QueryEscape(spanUID)
	jsonBody := fmt.Sprintf(`{"span_id": "%s", "span_uid": "%s"}`, traceparent.ParentID, encodedSpanUID)
	resp, err := http.Post(queryUrl, "application/json", strings.NewReader(jsonBody))

	if err != nil {
		log.Printf("Failed to report span ID: %v\n", err)
		return
	}

	defer resp.Body.Close()
}

func parseFaultload(tracestate tracing.TraceStateData) []string {
	faultload := tracestate.GetWithDefault("faultload", "")
	if faultload == "" {
		return nil
	}

	var decodedFaults []string
	for _, fault := range strings.Split(faultload, ":") {
		decodedFault, err := url.QueryUnescape(fault)
		if err != nil {
			log.Printf("Failed to decode fault: %v\n", err)
			continue
		}
		decodedFaults = append(decodedFaults, decodedFault)
	}

	return decodedFaults
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

		// Get "traceparent" and "tracestate" headers
		traceparent := r.Header.Get("traceparent")
		parent := tracing.ParseTraceParent(traceparent)

		if parent == nil {
			proxy.ServeHTTP(w, r)
			return
		}

		fmt.Printf("Received traced request: %s %s\n", r.Method, r.URL)

		tracestate := r.Header.Get("tracestate")
		state := tracing.ParseTraceState(tracestate)

		fmt.Printf("Traceparent: %+v\n", parent)
		fmt.Printf("Tracestate: %+v\n", state)

		// only forward the request if the "fit" flag is set
		shouldInspect := state.GetWithDefault("fit", "0") == "1"
		if !shouldInspect {
			proxy.ServeHTTP(w, r)
			return
		}

		// determine the span ID for the current request
		// and report the link to the parent span
		localSpanId := tracing.SpanIdFromRequest(r)
		log.Printf("local UID: %s\n", localSpanId)
		spanUID := localSpanId.String()

		reportSpanUID(*parent, spanUID)

		faultloadUids := parseFaultload(*state)
		log.Printf("Fault injection: %s\n", faultloadUids)

		for _, faultUid := range faultloadUids {
			log.Printf("Checking fault UID: %s=%s?\n", faultUid, spanUID)

			if faultUid == spanUID {
				log.Printf("Injecting fault: HTTP error\n")
				http.Error(w, "Injected fault: HTTP error", http.StatusInternalServerError)
				return
			}
		}

		// Forward the request to the target server
		proxy.ServeHTTP(w, r)
	})
}

func main() {
	// Set up the proxy host and target
	proxyHost := os.Getenv("PROXY_HOST")     // Proxy server address
	proxyTarget := os.Getenv("PROXY_TARGET") // Target server address

	useHttp2 := os.Getenv("USE_HTTP2") == "true"

	// Start an HTTP/2 server with a custom reverse proxy handler
	var httpServer *http.Server

	if useHttp2 {
		httpServer = &http.Server{
			Addr:    proxyHost,
			Handler: h2c.NewHandler(proxyHandler(proxyTarget, useHttp2), &http2.Server{}),
		}
	} else {
		httpServer = &http.Server{
			Addr:    proxyHost,
			Handler: proxyHandler(proxyTarget, useHttp2),
		}
	}

	// Start the reverse proxy server
	log.Printf("Starting reverse proxy on %s\n", proxyHost)
	err := httpServer.ListenAndServe()
	// err := httpServer.ListenAndServeTLS("cert.pem", "key.pem") // Requires SSL certificates for HTTP/2

	if err != nil {
		log.Fatalf("Error starting proxy server: %v\n", err)
	}
}
