package control

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"log/slog"
	"net"
	"net/http"
	_ "net/http/pprof"
	"os"
	"os/signal"
	"runtime"
	"strconv"
	"time"

	"dflipse.nl/ds-fit/controller/endpoints"
	"dflipse.nl/ds-fit/proxy/config"
	"dflipse.nl/ds-fit/proxy/tracing"
	"dflipse.nl/ds-fit/shared/faultload"
	"dflipse.nl/ds-fit/shared/util"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
)

var destination string

var usePPROF = os.Getenv("USE_PPROF") == "true"

func StartControlServer(config config.ControlConfig) {
	// Set up the proxy host and target
	// From https://pkg.go.dev/runtime/pprof

	// Handle SIGINT (CTRL+C) gracefully.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()

	// Set up OpenTelemetry.
	otelShutdown, err := util.SetupOTelSDK(ctx, config.UseTelemetry)
	if err != nil {
		slog.Error("Failed to set up OpenTelemetry", "err", err)
		return
	}

	// Handle shutdown properly so nothing leaks.
	defer func() {
		err = errors.Join(err, otelShutdown(context.Background()))
	}()

	// Start pprof server for profiling.
	if usePPROF {
		runtime.SetCPUProfileRate(500)
		go func() {
			slog.Info("Starting pprof server on :6060")
			log.Println(http.ListenAndServe("0.0.0.0:6060", nil))
		}()
	}

	destination = config.Destination
	controlPort := ":" + strconv.Itoa(config.Port)

	// Start HTTP server.
	srv := &http.Server{
		Addr:        controlPort,
		BaseContext: func(_ net.Listener) context.Context { return ctx },
		// ReadTimeout:  2 * time.Second,
		// WriteTimeout: 10 * time.Second,
		IdleTimeout: 120 * time.Second,
		Handler:     newHTTPHandler(),
	}

	srvErr := make(chan error, 1)

	go func() {
		slog.Info("Listening for control commands", "port", controlPort)
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
	newFaultload, err := faultload.ParseFaultloadRequest(r)
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(w, "Failed to parse request body: %v", err)
		return
	}

	faults := newFaultload.Faults
	myFaults := []faultload.Fault{}

	slog.Debug("------------- NEW TEST CASE ---------------")
	slog.Debug("Registering faultload", "size", len(newFaultload.Faults), "traceId", newFaultload.TraceId)
	for _, fault := range faults {
		if fault.Uid.Stack == nil {
			slog.Error("Fault UID stack is nil", "fault", fault)
			continue
		}

		lastIp := fault.Uid.Stack[len(fault.Uid.Stack)-1]
		if lastIp.Destination == destination {
			myFaults = append(myFaults, fault)
		}
	}

	slog.Info("Registered faults", "faults", len(myFaults), "traceId", newFaultload.TraceId)
	// Store the faultload for the given trace ID
	RegisteredFaults.Register(newFaultload.TraceId, myFaults)

	// Respond with a 200 OK
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, "OK")
}

// Handle the /v1/faultload/unregister endpoint
func unregisterFaultloadHandler(w http.ResponseWriter, r *http.Request) {
	// Parse the newFaultload from the request body
	var requestData endpoints.UnregisterFaultloadRequest
	err := json.NewDecoder(r.Body).Decode(&requestData)

	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(w, "Failed to parse request body: %v", err)
		return
	}
	traceId := requestData.TraceId

	slog.Info("Removed faults", "traceId", traceId)
	// Store the faultload for the given trace ID
	RegisteredFaults.Remove(traceId)
	tracing.ClearTracked(traceId)

	// Respond with a 200 OK
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, "OK")
}
