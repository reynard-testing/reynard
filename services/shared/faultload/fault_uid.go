package faultload

import (
	"crypto/sha256"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"regexp"
	"strings"
)

var (
	pathPrefix string = getEnvOrDefault("GRPC_PATH_PREFIX", "/")
)

func getEnvOrDefault(envVar, defaultValue string) string {
	value := os.Getenv(envVar)
	if value == "" {
		return defaultValue
	}
	return value
}

func PartialPointFromRequest(r *http.Request, destination string, maskPayload bool) PartialInjectionPoint {
	signature := getCallSignature(r)

	payload := "*"
	if !maskPayload {
		payload = getPayloadHash(r)
	}

	return PartialInjectionPoint{
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

	requestBodyBytes, err := io.ReadAll(body)
	if err != nil {
		slog.Warn("Failed to read request body", "err", err)
		return ""
	}
	// Reset the body so it can be read again
	// This is necessary because the proxy will read the body to forward the request
	r.Body = io.NopCloser(strings.NewReader(string(requestBodyBytes)))

	hash := ""
	if len(requestBodyBytes) > 0 {
		bodyHash := sha256.Sum256(requestBodyBytes)
		hash = fmt.Sprintf("%X", bodyHash)
	}

	// shortHash := hash[:8] // Use only the first 8 bytes of the hash
	// return fmt.Sprintf("%x", shortHash)
	return hash
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
