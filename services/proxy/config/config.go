package config

import (
	"os"
	"strconv"

	"dflipse.nl/fit-proxy/util"
)

type ProxyConfig struct {
	Host     string
	Target   string
	UseHttp2 bool
}

type ControlConfig struct {
	Port int
}

func GetProxyConfig() ProxyConfig {
	proxyHost := os.Getenv("PROXY_HOST") // Proxy server address

	proxyTarget := os.Getenv("PROXY_TARGET") // Target server address

	useHttp2 := os.Getenv("USE_HTTP2") == "true"

	return ProxyConfig{
		Host:     proxyHost,
		Target:   proxyTarget,
		UseHttp2: useHttp2,
	}
}

func GetControlConfig(proxyConfig ProxyConfig) ControlConfig {
	if controllerPort := os.Getenv("CONTROLLER_PORT"); controllerPort != "" {
		if controllerPortInt, err := strconv.Atoi(controllerPort); err == nil {
			return ControlConfig{
				Port: controllerPortInt,
			}
		}
	}

	_, proxyPort := util.AsHostPort(proxyConfig.Host)
	controlPort := proxyPort + 1

	return ControlConfig{
		Port: controlPort,
	}
}
