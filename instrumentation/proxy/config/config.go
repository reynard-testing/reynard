package config

import (
	"os"
	"strconv"

	"go.reynard.dev/instrumentation/shared/util"
)

type ProxyConfig struct {
	Host        string
	Target      string
	Destination string
}

type ControlConfig struct {
	Port         int
	Destination  string
	UseTelemetry bool
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

func GetControlConfig(proxyConfig ProxyConfig) ControlConfig {
	UseTelemetry := os.Getenv("USE_OTEL") == "true"

	if controllerPort := os.Getenv("CONTROL_PORT"); controllerPort != "" {
		if controllerPortInt, err := strconv.Atoi(controllerPort); err == nil {
			return ControlConfig{
				UseTelemetry: UseTelemetry,
				Port:         controllerPortInt,
				Destination:  proxyConfig.Destination,
			}
		}
	}

	_, proxyPort := util.AsHostAndPort(proxyConfig.Host)
	controlPort := proxyPort + 1

	return ControlConfig{
		UseTelemetry: UseTelemetry,
		Port:         controlPort,
		Destination:  proxyConfig.Destination,
	}
}
