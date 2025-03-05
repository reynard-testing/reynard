package util

import (
	"net"
	"strconv"
	"strings"
)

func AsHostAndPortFromUrl(url string) string {
	parts := strings.Split(url, "://")
	if len(parts) < 2 {
		return url
	}

	return parts[1]
}

func AsHostAndPort(hostAndPort string) (string, int) {
	host, port, err := net.SplitHostPort(hostAndPort)
	if err != nil {
		return "", 0
	}

	intPort, err := strconv.Atoi(port)

	if err != nil {
		return host, 0
	}

	return host, intPort
}
