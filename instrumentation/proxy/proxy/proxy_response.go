package proxy

import (
	"bytes"
	"crypto/sha256"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strconv"

	"go.reynard.dev/instrumentation/shared/trace"
)

type ResponseCapture struct {
	http.ResponseWriter
	Status          int
	CapturedBuffer  bytes.Buffer
	HeaderMap       http.Header
	wroteHeader     bool
	DirectlyForward bool
}

func NewResponseCapture(w http.ResponseWriter, directlyForward bool) *ResponseCapture {
	return &ResponseCapture{
		ResponseWriter:  w,
		Status:          http.StatusOK,
		HeaderMap:       make(http.Header),
		DirectlyForward: directlyForward,
	}
}

func (rc *ResponseCapture) Header() http.Header {
	if rc.DirectlyForward {
		// If we are directly forwarding, we need to use the original header
		return rc.ResponseWriter.Header()
	}

	return rc.HeaderMap
}

func (rc *ResponseCapture) WriteHeader(statusCode int) {
	if rc.DirectlyForward {
		// If we are directly forwarding, we need to use the original header
		rc.Status = statusCode
		rc.ResponseWriter.WriteHeader(statusCode)
		return
	}

	// only write the header if it hasn't been written yet
	if rc.wroteHeader {
		return
	}

	rc.Status = statusCode
	rc.wroteHeader = true
}

func (rc *ResponseCapture) Write(b []byte) (int, error) {
	if rc.DirectlyForward {
		rc.CapturedBuffer.Write(b)
		return rc.ResponseWriter.Write(b)
	}

	if !rc.wroteHeader {
		rc.WriteHeader(http.StatusOK)
	}

	return rc.CapturedBuffer.Write(b)
}

func (rc *ResponseCapture) Flush(logHeaders bool) error {
	if rc.DirectlyForward {
		// Already flushed, no need to do it again
		return nil
	}

	// Copy headers
	for k, vv := range rc.HeaderMap {
		for _, v := range vv {
			rc.ResponseWriter.Header().Add(k, v)
			if logHeaders {
				slog.Debug("Writing Header", "key", k, "value", v)
			}
		}
	}

	// Send status code
	rc.ResponseWriter.WriteHeader(rc.Status)

	// Write the full body
	written, err := io.Copy(rc.ResponseWriter, &rc.CapturedBuffer)
	slog.Debug("Flushing response", "status", rc.Status, "bytesWritten", written)

	if err != nil {
		return err
	}

	return nil
}

type NoOpResponseWriter struct{}

func (n *NoOpResponseWriter) Header() http.Header {
	return http.Header{}
}

func (n *NoOpResponseWriter) Write([]byte) (int, error) {
	return 0, nil
}

func (n *NoOpResponseWriter) WriteHeader(statusCode int) {}

func (rc *ResponseCapture) GetResponseData(hashBody bool) trace.ResponseData {
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
		bodyBytes := rc.CapturedBuffer.Bytes()
		body = string(bodyBytes)
	}

	if hashBody && len(body) > 0 {
		bodyHash := sha256.Sum256([]byte(body))
		body = fmt.Sprintf("%X", bodyHash)
	}

	return trace.ResponseData{
		Status: status,
		Body:   body,
	}
}
