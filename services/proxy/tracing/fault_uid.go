package tracing

import (
	"crypto/sha256"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"regexp"
	"strings"

	"dflipse.nl/fit-proxy/faultload"
)

var (
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

func FaultUidFromRequest(r *http.Request, destination string, maskPayload, isInitial bool) faultload.FaultUid {
	traceId := getTraceId(r)
	signature := getCallSignature(r)

	origin := "<none>"
	if !isInitial {
		origin = getOrigin(r)
	}

	// destination := getDestination(r)
	payload := "*"
	if !maskPayload {
		payload = getPayloadHash(r)
	}
	invocationCount := getInvocationCount(origin, signature, payload, traceId)

	return faultload.FaultUid{
		Origin:      origin,
		Destination: destination,
		Signature:   signature,
		Payload:     payload,
		Count:       invocationCount,
	}
}

func getInvocationCount(origin, signature, payload, traceId string) int {
	key := fmt.Sprintf("%s-%s-%s-%s", origin, signature, payload, traceId)
	currentIndex := traceInvocationCounter.GetCount(key)
	return currentIndex
}

func getPayloadHash(r *http.Request) string {
	body := r.Body

	if body == nil {
		return ""
	}

	bodyBytes, err := io.ReadAll(body)
	if err != nil {
		log.Printf("Failed to read request body: %v\n", err)
		return ""
	}
	// Reset the body so it can be read again
	// This is necessary because the proxy will read the body to forward the request
	r.Body = io.NopCloser(strings.NewReader(string(bodyBytes)))

	hash := sha256.Sum256(bodyBytes)
	// shortHash := hash[:8] // Use only the first 8 bytes of the hash
	// return fmt.Sprintf("%x", shortHash)
	return fmt.Sprintf("%x", hash)
}

func getTraceId(r *http.Request) string {
	traceParentHeader := r.Header.Get("traceparent")
	parts := strings.Split(traceParentHeader, "-")
	if len(parts) < 4 {
		return ""
	}

	return parts[1]
}

var digitCheck = regexp.MustCompile(`^[0-9]+$`)
var uuidCheck = regexp.MustCompile("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-4[a-fA-F0-9]{3}-[8|9|aA|bB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}$")

func normalizeUrl(urlPath string) string {
	seperator := "/"
	parts := strings.Split(urlPath, seperator)
	for i, part := range parts {
		if digitCheck.Match([]byte(part)) {
			parts[i] = "[id]"
		} else if uuidCheck.Match([]byte(part)) {
			parts[i] = "[uuid]"
		}
	}

	return strings.Join(parts, seperator)
}

func getCallSignature(r *http.Request) string {
	url := r.URL
	pathOnly := url.Path

	// https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md
	contentType := r.Header.Get("Content-Type")
	if contentType == "application/grpc" {
		withoutPrefix := strings.TrimPrefix(pathOnly, pathPrefix)
		return withoutPrefix
	} else {
		return r.Method + " " + normalizeUrl(pathOnly)
	}
}

func GetHostIdentifier(addr string) string {
	names, err := net.LookupAddr(addr)
	if err != nil || len(names) == 0 {
		// Handle the case where no hostname is found
		log.Printf("No hostname for: %s\n", addr)
		return addr // Return the IP as fallback
	}
	// Extract service name from the FQDN
	log.Printf("Hostnames: %s\n", names)
	fqdn := names[0]
	parts := strings.Split(fqdn, ".")
	if len(parts) == 0 {
		return fqdn
	}

	// In docker, the name is [stack]-[service]-[index].[stack]_[network]
	prefix := stackPrefix
	if prefix == "" {
		domain_name := parts[1]
		domain_parts := strings.Split(domain_name, "_")
		prefix = domain_parts[0]
	}

	serviceName := parts[0]
	// remove [stack]- from the service name
	serviceWithoutPrefix := strings.TrimPrefix(serviceName+"-", prefix)
	// remove -[index] from the service name
	serviceWithoutIndex := regexp.MustCompile(`-\d+$`).ReplaceAllString(serviceWithoutPrefix, "")

	return serviceWithoutIndex
}

// Returns the hostname of the service that made the request
func getOrigin(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		log.Printf("Failed to get originating service: %v\n", err)
		return "<none>"
	}

	log.Printf("Remote address: %s\n", r.RemoteAddr)
	return GetHostIdentifier(host)
}

// Deprecated, use the config's destination instead
// func getDestination(r *http.Request) string {
// 	host, _, err := net.SplitHostPort(r.Host)
// 	if err != nil {
// 		log.Printf("Failed to get destination: %v\n", err)
// 		return "<none>"
// 	}

// 	return GetHostIdentifier(host)
// }
