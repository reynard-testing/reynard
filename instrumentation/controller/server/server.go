package server

import (
	"context"
	"errors"
	"log"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strconv"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.reynard.dev/instrumentation/controller/endpoints"
	"go.reynard.dev/instrumentation/shared/util"
)

func StartController(port int, useOTEL bool) (err error) {
	logLevel := util.GetLogLevel()

	// Create handler
	handler := slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: logLevel,
	})

	// Set default logger
	slog.SetDefault(slog.New(handler))
	log.SetFlags(log.LstdFlags | log.Lmicroseconds) // Ensure timestamps are logged
	slog.Info("Logging", "level", logLevel)

	slog.Info("Registered proxies", "proxyList", endpoints.ProxyList)
	slog.Info("OTEL", "enabled", useOTEL)

	// Handle SIGINT (CTRL+C) gracefully.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()

	// Set up OpenTelemetry.
	otelShutdown, err := util.SetupOTelSDK(ctx, useOTEL)
	if err != nil {
		slog.Error("Failed to set up OpenTelemetry", "err", err)
		return
	}

	// Handle shutdown properly so nothing leaks.
	defer func() {
		err = errors.Join(err, otelShutdown(context.Background()))
	}()

	controlPort := ":" + strconv.Itoa(port)

	// Start HTTP server.
	srv := &http.Server{
		Addr:        controlPort,
		BaseContext: func(_ net.Listener) context.Context { return ctx },
		Handler:     newHTTPHandler(),
	}

	srvErr := make(chan error, 1)
	go func() {
		slog.Info("Controller is listening", "port", controlPort)
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
	return
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

	handleFunc("GET /v1/trace/{trace_id}", endpoints.GetReportsByTraceID)
	handleFunc("POST /v1/proxy/report", endpoints.ReportSpanId)
	handleFunc("POST /v1/proxy/get-uid", endpoints.GetFaultUid)
	handleFunc("POST /v1/faultload/register", endpoints.RegisterFaultloadsAtProxies)
	handleFunc("POST /v1/faultload/unregister", endpoints.UnregisterFaultloadsAtProxies)
	handleFunc("GET /v1/clear", endpoints.ClearAll)

	// Add HTTP instrumentation for the whole server.
	handler := otelhttp.NewHandler(mux, "/")
	return handler
}
