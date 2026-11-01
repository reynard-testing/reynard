package main

import (
	"context"
	"log"
	"log/slog"
	"net/http"
	_ "net/http/pprof"
	"os"
	"runtime"

	"go.reynard.dev/instrumentation/proxy/config"
	"go.reynard.dev/instrumentation/proxy/extension"
	"go.reynard.dev/instrumentation/proxy/proxy"
	"go.reynard.dev/instrumentation/shared/util"
)

func main() {
	logLevel := util.GetLogLevel()

	// Create handler
	handler := slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: logLevel,
	})

	// Set default logger
	slog.SetDefault(slog.New(handler))
	slog.Info("Logging", "level", logLevel)
	log.SetFlags(log.LstdFlags | log.Lmicroseconds) // Ensure timestamps are logged

	// Set up OpenTelemetry.
	otelShutdown, err := util.SetupOTelSDK(context.Background(), os.Getenv("USE_OTEL") == "true")
	if err != nil {
		slog.Error("Failed to set up OpenTelemetry", "err", err)
		return
	}
	defer otelShutdown(context.Background())

	// Start pprof server for profiling.
	if os.Getenv("USE_PPROF") == "true" {
		runtime.SetCPUProfileRate(500)
		go func() {
			slog.Info("Starting pprof server on :6060")
			log.Println(http.ListenAndServe("0.0.0.0:6060", nil))
		}()
	}

	// Inside Lambda, the proxy is deployed as an extension layer
	if runtimeApi := os.Getenv("AWS_LAMBDA_RUNTIME_API"); runtimeApi != "" {
		go func() {
			extension.Run(runtimeApi)
			os.Exit(0)
		}()
	}

	// Start the reverse proxy server
	proxy.StartProxy(config.GetProxyConfig())
}
