package tracing

import (
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"strings"
)

var traceInvocationCounter map[string]int = make(map[string]int)

// var serviceName string = os.Getenv("SERVICE_NAME")
var stackName string = os.Getenv("STACK_NAME")
var pathPrefix string = getEnvOrDefault("GRPC_PATH_PREFIX", "/")

func getEnvOrDefault(envVar, defaultValue string) string {
	value := os.Getenv(envVar)
	if value == "" {
		return defaultValue
	}
	return value
}

type SpanIdentifier struct {
	traceId         string
	clientName      string
	signature       string
	invocationIndex int
}

func (s SpanIdentifier) String() string {
	return fmt.Sprintf("%s>%s|%d", s.clientName, s.signature, s.invocationIndex)
}

func SpanIdFromRequest(r *http.Request) SpanIdentifier {
	traceId := getTraceId(r)
	signature := getCallSignature(r)
	clientName := getOriginatingService(r)
	invocationIndex := getInvocationIndex(clientName, signature, traceId)

	return SpanIdentifier{
		clientName:      clientName,
		traceId:         traceId,
		signature:       signature,
		invocationIndex: invocationIndex,
	}
}

func getInvocationIndex(clientName, signature, traceId string) int {
	key := fmt.Sprintf("%s-%s-%s", clientName, signature, traceId)
	currentIndex, exists := traceInvocationCounter[key]

	if !exists {
		currentIndex = 0
	} else {
		currentIndex++
	}

	traceInvocationCounter[key] = currentIndex
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

	names, err := net.LookupAddr(host)
	if err != nil || len(names) == 0 {
		// Handle the case where no hostname is found
		return host // Return the IP as fallback
	}
	// Extract service name from the FQDN
	log.Print(names)
	fqdn := names[0]
	parts := strings.Split(fqdn, ".")
	if len(parts) == 0 {
		return fqdn
	}

	fullStackName := parts[0] // The first part of the FQDN is usually the service name
	serviceId := strings.TrimPrefix(fullStackName, stackName+"-")

	// Split the service name on "-"
	serviceParts := strings.Split(serviceId, "-")
	if len(serviceParts) == 0 {
		return serviceId
	}

	serviceName := serviceParts[0]
	return serviceName
}
