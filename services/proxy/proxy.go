package main

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"strconv"

	"dflipse.nl/fit-proxy/faultload"
	"dflipse.nl/fit-proxy/tracing"
	"golang.org/x/net/http2"
	"golang.org/x/net/http2/h2c"
)

var queryHost string = os.Getenv("ORCHESTRATOR_HOST")
var registeredFaults map[string][]faultload.Fault = make(map[string][]faultload.Fault)

func reportSpanUID(traceparent tracing.TraceParentData, spanUID string, faultInjected bool) {
	queryUrl := fmt.Sprintf("http://%s/v1/link", queryHost)

	data := map[string]interface{}{
		"span_id":        traceparent.ParentID,
		"span_uid":       spanUID,
		"fault_injected": faultInjected,
	}

	jsonBodyBytes, err := json.Marshal(data)
	if err != nil {
		log.Printf("Failed to marshal JSON: %v\n", err)
		return
	}

	resp, err := http.Post(queryUrl, "application/json", bytes.NewBuffer(jsonBodyBytes))

	if err != nil {
		log.Printf("Failed to report span ID: %v\n", err)
		return
	}

	defer resp.Body.Close()
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

		faults, ok := registeredFaults[parent.TraceID]
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

		fmt.Printf("Received injectable request: %s %s\n", r.Method, r.URL)
		fmt.Printf("Traceparent: %+v\n", parent)
		fmt.Printf("Tracestate: %+v\n", state)

		// determine the span ID for the current request
		// and report the link to the parent span
		localSpanId := tracing.SpanIdFromRequest(r)
		log.Printf("Local UID: %s\n", localSpanId)
		spanUID := localSpanId.String()

		log.Printf("Fault registered: %s\n", faults)

		for _, fault := range faults {
			if fault.SpanUID == spanUID {
				log.Printf("Performing fault: %s\n", fault)
				reportSpanUID(*parent, spanUID, true)
				fault.Perform(proxy, w, r)
				return
			}
		}

		reportSpanUID(*parent, spanUID, false)
		// Forward the request to the target server
		proxy.ServeHTTP(w, r)
	})
}

// Handle the /v1/register_faultload endpoint
func registerFaultloadHandler(w http.ResponseWriter, r *http.Request) {
	// Parse the faultload from the request body
	faults, traceId, err := faultload.ParseRequest(r)
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(w, "Failed to parse request body: %v", err)
		return
	}

	// Store the faultload for the given trace ID
	registeredFaults[traceId] = faults

	// Respond with a 200 OK
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, "OK")
}

func asHostPort(hostAndPort string) (string, int) {
	host, port, err := net.SplitHostPort(hostAndPort)
	if err != nil {
		return "", 0
	}

	intPort, err := strconv.Atoi(port)

	if err != nil {
		return host, 0
	}

	return host, intPort
}

func main() {
	// Set up the proxy host and target
	proxyHost := os.Getenv("PROXY_HOST") // Proxy server address
	_, hostPort := asHostPort(proxyHost)
	controlPort := hostPort + 1
	proxyTarget := os.Getenv("PROXY_TARGET") // Target server address

	useHttp2 := os.Getenv("USE_HTTP2") == "true"

	log.Printf("Reverse proxy for: %s\n", proxyTarget)
	log.Printf("Reachable at: %s\n", proxyHost)

	// Start an HTTP/2 server with a custom reverse proxy handler
	var httpServer *http.Server

	if useHttp2 {
		log.Printf("Using HTTP/2\n")
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
	go func() {
		err := httpServer.ListenAndServe()
		// err := httpServer.ListenAndServeTLS("cert.pem", "key.pem") // Requires SSL certificates for HTTP/2

		if err != nil {
			log.Fatalf("Error starting proxy server: %v\n", err)
		}
	}()

	// Start the control server
	registerFaultloadPort := ":" + strconv.Itoa(controlPort)
	http.HandleFunc("/v1/register_faultload", registerFaultloadHandler)
	err := http.ListenAndServe(registerFaultloadPort, nil)
	log.Printf("Listening for control commands on port %s\n", registerFaultloadPort)

	if err != nil {
		log.Fatalf("Error starting register faultload server: %v\n", err)
	}
}
