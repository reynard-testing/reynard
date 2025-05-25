package main

import (
	"log"
	"log/slog"
	"os"

	"dflipse.nl/ds-fit/proxy/config"
	"dflipse.nl/ds-fit/proxy/control"
	"dflipse.nl/ds-fit/proxy/proxy"
	"dflipse.nl/ds-fit/shared/util"
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
	controlConfig := config.GetControlConfig(proxyConfig)

	// Start the reverse proxy server
	go func() {
		proxy.StartProxy(proxyConfig)
	}()

	// Start the control server
	control.StartControlServer(controlConfig)
}
