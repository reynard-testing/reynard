package util

import (
	"net"
	"strconv"
)

func AsHostPort(hostAndPort string) (string, int) {
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
