package main

import (
	"log"
	"log/slog"
	"os"

	"go.reynard.dev/instrumentation/proxy/config"
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

	proxyConfig := config.GetProxyConfig()

	// Start the reverse proxy server
	proxy.StartProxy(proxyConfig)
}
