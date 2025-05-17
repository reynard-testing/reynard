package util

import (
	"log/slog"
	"net"
	"os"
	"regexp"
	"strconv"
	"strings"
)

var stackPrefix string = os.Getenv("STACK_PREFIX")

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

var postfixRegex = regexp.MustCompile(`-\d+$`)

func GetHostIdentifier(addr string) string {
	names, err := net.LookupAddr(addr)
	if err != nil || len(names) == 0 {
		// Handle the case where no hostname is found
		slog.Warn("Failed to resolve hostname", "addr", addr, "err", err)
		return addr // Return the IP as fallback
	}
	// Extract service name from the FQDN
	slog.Debug("Hostnames", "names", names)
	fqdn := names[0]
	parts := strings.Split(fqdn, ".")
	if len(parts) == 0 {
		return fqdn
	}

	// In docker, the name is [stack]-[service]-[index].[stack]_[network]
	prefix := stackPrefix
	if prefix == "" {
		domainName := parts[1]
		domainParts := strings.Split(domainName, "_")
		prefix = domainParts[0]
	}

	fullServiceName := parts[0]
	// remove [stack]- from the service name
	serviceWithoutPrefix := strings.TrimPrefix(fullServiceName, prefix+"-")
	// remove -[index] from the service name
	serviceWithoutIndex := postfixRegex.ReplaceAllString(serviceWithoutPrefix, "")

	return serviceWithoutIndex
}
