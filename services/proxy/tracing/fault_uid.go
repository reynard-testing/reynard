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

func PartialPointFromRequest(r *http.Request, destination string, maskPayload bool) faultload.PartialInjectionPoint {
	signature := getCallSignature(r)

	payload := "*"
	if !maskPayload {
		payload = getPayloadHash(r)
	}

	return faultload.PartialInjectionPoint{
		Destination: destination,
		Signature:   signature,
		Payload:     payload,
	}
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

	hash := ""
	if len(bodyBytes) > 0 {
		bodyHash := sha256.Sum256(bodyBytes)
		hash = fmt.Sprintf("%x", bodyHash)
	}

	// shortHash := hash[:8] // Use only the first 8 bytes of the hash
	// return fmt.Sprintf("%x", shortHash)
	return fmt.Sprintf("%x", hash)
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

var postfixRegex = regexp.MustCompile(`-\d+$`)

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
