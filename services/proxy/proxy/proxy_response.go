package proxy

import (
	"bytes"
	"crypto/sha256"
	"fmt"
	"net/http"
	"strconv"

	"dflipse.nl/fit-proxy/tracing"
)

type ResponseCapture struct {
	http.ResponseWriter
	Status     int
	BodyBuffer bytes.Buffer // Buffer to capture the body
}

func (rc *ResponseCapture) WriteHeader(statusCode int) {
	rc.Status = statusCode
	rc.ResponseWriter.WriteHeader(statusCode)
}

func (rc *ResponseCapture) Write(b []byte) (int, error) {
	rc.BodyBuffer.Write(b)            // Capture body in buffer
	return rc.ResponseWriter.Write(b) // Write to the actual response
}

type NoOpResponseWriter struct{}

func (n *NoOpResponseWriter) Header() http.Header {
	return http.Header{}
}

func (n *NoOpResponseWriter) Write([]byte) (int, error) {
	return 0, nil
}

func (n *NoOpResponseWriter) WriteHeader(statusCode int) {}

func (rc *ResponseCapture) GetResponseData(hashBody bool) tracing.ResponseData {
	body := rc.BodyBuffer.String()
	status := rc.Status

	if rc.Header().Get("Content-Type") == "application/grpc" {
		statusHeader := rc.Header().Get("grpc-status")
		statusCode, err := strconv.Atoi(statusHeader)
		if err == nil && statusCode != 0 {
			body = rc.Header().Get("grpc-message")
			status = toHttpError(statusCode)
		}
	}

	if hashBody {
		bodyHash := sha256.Sum256([]byte(body))
		body = fmt.Sprintf("%x", bodyHash)
	}

	return tracing.ResponseData{
		Status: status,
		Body:   body,
	}
}
