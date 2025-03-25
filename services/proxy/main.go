package main

import (
	"log"

	"dflipse.nl/fit-proxy/config"
	"dflipse.nl/fit-proxy/control"
	"dflipse.nl/fit-proxy/proxy"
)

func main() {
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
