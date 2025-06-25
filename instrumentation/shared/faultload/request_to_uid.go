package faultload

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"log/slog"
	"net/http"
	"os"
	"regexp"
	"sort"
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

	var payload *string
	if !maskPayload {
		payloadHash := getPayloadHash(r)
		payload = &payloadHash
	}

	return PartialInjectionPoint{
		Destination: &destination,
		Signature:   &signature,
		Payload:     payload,
	}
}

func getPayloadHash(r *http.Request) string {
	var payloadBytes []byte

	// 1. Handle Request Body
	if r.Body != nil {
		bodyBytes, err := io.ReadAll(r.Body)
		if err != nil {
			slog.Error("Failed to read request body", "error", err)
			return ""
		}

		// Restore the body for subsequent reads
		r.Body = io.NopCloser(bytes.NewBuffer(bodyBytes))
		payloadBytes = append(payloadBytes, bodyBytes...)
	}

	// 2. Handle Request Query Parameters
	if len(r.URL.RawQuery) > 0 {
		queryValues := r.URL.Query()

		// Sort query parameters by key to ensure consistent hashing
		// regardless of the order they appear in the URL.
		keys := make([]string, 0, len(queryValues))
		for k := range queryValues {
			keys = append(keys, k)
		}
		sort.Strings(keys)

		for _, key := range keys {
			values := queryValues[key]
			sort.Strings(values) // Sort values for multi-value parameters
			payloadBytes = append(payloadBytes, []byte(key)...)
			payloadBytes = append(payloadBytes, []byte("=")...)
			for _, val := range values {
				payloadBytes = append(payloadBytes, []byte(val)...)
			}
		}
	}

	// 3. Check if payload is empty
	if len(payloadBytes) == 0 {
		return ""
	}

	// 4. Calculate SHA256 Hash
	hasher := sha256.New()
	hasher.Write(payloadBytes)
	return hex.EncodeToString(hasher.Sum(nil))
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
