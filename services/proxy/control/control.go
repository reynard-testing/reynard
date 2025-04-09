package control

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"time"

	"dflipse.nl/fit-proxy/config"
	"dflipse.nl/fit-proxy/faultload"
	"dflipse.nl/fit-proxy/tracing"
	"dflipse.nl/fit-proxy/util"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
)

var destination string

func StartControlServer(config config.ControlConfig) {
	// Handle SIGINT (CTRL+C) gracefully.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()

	// Set up OpenTelemetry.
	otelShutdown, err := util.SetupOTelSDK(ctx, config.UseTelemetry)
	if err != nil {
		log.Printf("Failed to set up OpenTelemetry: %v\n", err)
		return
	}

	// Handle shutdown properly so nothing leaks.
	defer func() {
		err = errors.Join(err, otelShutdown(context.Background()))
	}()

	destination = config.Destination
	controlPort := ":" + strconv.Itoa(config.Port)

	// Start HTTP server.
	srv := &http.Server{
		Addr:         controlPort,
		BaseContext:  func(_ net.Listener) context.Context { return ctx },
		ReadTimeout:  time.Second,
		WriteTimeout: 10 * time.Second,
		Handler:      newHTTPHandler(),
	}

	srvErr := make(chan error, 1)

	go func() {
		log.Printf("Listening for control commands on port %s\n", controlPort)
		srvErr <- srv.ListenAndServe()
	}()

	// Wait for interruption.
	select {
	case err = <-srvErr:
		// Error when starting HTTP server.
		return
	case <-ctx.Done():
		// Wait for first CTRL+C.
		// Stop receiving signal notifications as soon as possible.
		stop()
	}

	// When Shutdown is called, ListenAndServe immediately returns ErrServerClosed.
	err = srv.Shutdown(context.Background())
}

func newHTTPHandler() http.Handler {
	mux := http.NewServeMux()

	// handleFunc is a replacement for mux.HandleFunc
	// which enriches the handler's HTTP instrumentation with the pattern as the http.route.
	handleFunc := func(pattern string, handlerFunc func(http.ResponseWriter, *http.Request)) {
		// Configure the "http.route" for the HTTP instrumentation.
		handler := otelhttp.WithRouteTag(pattern, http.HandlerFunc(handlerFunc))
		mux.Handle(pattern, handler)
	}

	// Register handlers.
	handleFunc("/v1/faultload/register", registerFaultloadHandler)
	handleFunc("/v1/faultload/unregister", unregisterFaultloadHandler)

	// Add HTTP instrumentation for the whole server.
	handler := otelhttp.NewHandler(mux, "/")
	return handler
}

// Handle the /v1/faultload/register endpoint
func registerFaultloadHandler(w http.ResponseWriter, r *http.Request) {
	// Parse the newFaultload from the request body
	newFaultload, err := faultload.ParseRequest(r)
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(w, "Failed to parse request body: %v", err)
		return
	}

	faults := newFaultload.Faults
	myFaults := []faultload.Fault{}

	log.Printf("\n----------------------------\n")
	log.Printf("Registering faultload (size=%d) for trace ID %s\n", len(newFaultload.Faults), newFaultload.TraceId)
	for _, fault := range faults {
		lastIp := fault.Uid[len(fault.Uid)-1]
		if lastIp.Destination == destination {
			myFaults = append(myFaults, fault)
		}
	}

	log.Printf("Registered %d faults for trace ID %s\n", len(myFaults), newFaultload.TraceId)
	// Store the faultload for the given trace ID
	RegisteredFaults.Register(newFaultload.TraceId, myFaults)

	// Respond with a 200 OK
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, "OK")
}

type UnregisterFaultloadRequest struct {
	TraceId string `json:"trace_id"`
}

// Handle the /v1/faultload/unregister endpoint
func unregisterFaultloadHandler(w http.ResponseWriter, r *http.Request) {
	// Parse the newFaultload from the request body
	var requestData UnregisterFaultloadRequest
	err := json.NewDecoder(r.Body).Decode(&requestData)

	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(w, "Failed to parse request body: %v", err)
		return
	}
	traceId := requestData.TraceId

	log.Printf("Removed faults for trace ID %s\n", traceId)
	// Store the faultload for the given trace ID
	RegisteredFaults.Remove(traceId)
	tracing.ClearTraceCount(traceId)
	tracing.ClearTracked(traceId)

	// Respond with a 200 OK
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, "OK")
}
