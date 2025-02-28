package tracing

import (
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"strings"

	"dflipse.nl/fit-proxy/faultload"
)

var (
	ServiceName string = os.Getenv("SERVICE_NAME")
	stackPrefix string = os.Getenv("STACK_PREFIX")
	pathPrefix  string = getEnvOrDefault("GRPC_PATH_PREFIX", "/")
)

func getEnvOrDefault(envVar, defaultValue string) string {
	value := os.Getenv(envVar)
	if value == "" {
		return defaultValue
	}
	return value
}

func FaultUidFromRequest(r *http.Request) faultload.FaultUid {
	traceId := getTraceId(r)
	signature := getCallSignature(r)
	clientName := getOriginatingService(r)
	// clientName := r.RemoteAddr
	invocationCount := getInvocationCount(clientName, signature, traceId)

	return faultload.FaultUid{
		Origin:      clientName,
		Destination: ServiceName,
		Signature:   signature,
		Count:       invocationCount,
	}
}

func getInvocationCount(clientName, signature, traceId string) int {
	key := fmt.Sprintf("%s-%s-%s", clientName, signature, traceId)
	currentIndex := traceInvocationCounter.GetCount(key)
	return currentIndex
}

func getTraceId(r *http.Request) string {
	traceParentHeader := r.Header.Get("traceparent")
	parts := strings.Split(traceParentHeader, "-")
	if len(parts) < 4 {
		return ""
	}

	return parts[1]
}

func getCallSignature(r *http.Request) string {
	url := r.URL
	pathOnly := url.Path

	contentType := r.Header.Get("Content-Type")
	if contentType == "application/grpc" {
		withoutPrefix := strings.TrimPrefix(pathOnly, pathPrefix)
		return withoutPrefix
	}

	return pathOnly
}

// Returns the hostname of the service that made the request
func getOriginatingService(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		log.Printf("Failed to get originating service: %v\n", err)
		return "<none>"
	}

	log.Printf("Remote address: %s\n", r.RemoteAddr)
	names, err := net.LookupAddr(host)
	if err != nil || len(names) == 0 {
		// Handle the case where no hostname is found
		return host // Return the IP as fallback
	}
	// Extract service name from the FQDN
	log.Printf("Hostnames: %s\n", names)
	fqdn := names[0]
	parts := strings.Split(fqdn, ".")
	if len(parts) == 0 {
		return fqdn
	}

	serviceName := parts[0]
	serviceWithoutPrefix := strings.TrimPrefix(serviceName, stackPrefix)
	return serviceWithoutPrefix
}
