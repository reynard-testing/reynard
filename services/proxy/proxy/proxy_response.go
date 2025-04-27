package proxy

import (
	"bytes"
	"crypto/sha256"
	"fmt"
	"io"
	"net/http"
	"strconv"

	"dflipse.nl/fit-proxy/tracing"
)

type ResponseCapture struct {
	http.ResponseWriter
	Status      int
	BodyBuffer  bytes.Buffer // Buffer to capture the body
	HeaderMap   http.Header
	wroteHeader bool
}

func NewResponseCapture(w http.ResponseWriter) *ResponseCapture {
	return &ResponseCapture{
		ResponseWriter: w,
		Status:         http.StatusOK,
		HeaderMap:      make(http.Header),
	}
}

func (rc *ResponseCapture) Header() http.Header {
	return rc.HeaderMap
}

func (rc *ResponseCapture) WriteHeader(statusCode int) {
	if rc.wroteHeader {
		return
	}
	rc.Status = statusCode
	rc.wroteHeader = true
}

func (rc *ResponseCapture) Write(b []byte) (int, error) {
	if !rc.wroteHeader {
		rc.WriteHeader(http.StatusOK)
	}
	return rc.BodyBuffer.Write(b)
}

func (rc *ResponseCapture) Flush() error {
	// Copy headers
	for k, vv := range rc.HeaderMap {
		for _, v := range vv {
			rc.ResponseWriter.Header().Add(k, v)
		}
	}

	// Send status code
	rc.ResponseWriter.WriteHeader(rc.Status)

	// Write the full body
	_, err := io.Copy(rc.ResponseWriter, &rc.BodyBuffer)
	return err
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
	body := ""
	status := rc.Status

	// Check if the response is gRPC
	if rc.Header().Get("Content-Type") == "application/grpc" {
		statusHeader := rc.Header().Get("grpc-status")
		statusCode, err := strconv.Atoi(statusHeader)
		if err == nil && statusCode != 0 {
			body = rc.Header().Get("grpc-message")
			status = toHttpError(statusCode)
		}
	} else {
		body = rc.BodyBuffer.String()
	}

	if hashBody && len(body) > 0 {
		bodyHash := sha256.Sum256(rc.BodyBuffer.Bytes())
		body = fmt.Sprintf("%X", bodyHash)
	}

	return tracing.ResponseData{
		Status: status,
		Body:   body,
	}
}
