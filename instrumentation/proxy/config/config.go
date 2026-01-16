package config

import (
	"os"

	"go.reynard.dev/instrumentation/shared/util"
)

type ProxyConfig struct {
	Host        string
	Target      string
	Destination string
}

func GetProxyConfig() ProxyConfig {
	proxyHost := os.Getenv("PROXY_HOST")     // Proxy server address
	proxyTarget := os.Getenv("PROXY_TARGET") // Target server address

	// Use either predefined service name or fallback to target host
	destination := os.Getenv("SERVICE_NAME")

	if destination == "" {
		destination = os.Getenv("OTEL_SERVICE_NAME")
	}

	if destination == "" {
		proxyTargetAddr := util.AsHostAndPortFromUrl(proxyTarget)
		proxyTargetHost, _ := util.AsHostAndPort(proxyTargetAddr)
		destination = util.GetHostIdentifier(proxyTargetHost)
	}

	return ProxyConfig{
		Host:        proxyHost,
		Target:      proxyTarget,
		Destination: destination,
	}
}
