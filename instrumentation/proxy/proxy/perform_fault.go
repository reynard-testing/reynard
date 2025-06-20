package proxy

import (
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"go.reynard.dev/instrumentation/shared/faultload"
)

func performHttpError(f faultload.Fault, s *ProxyState, omit bool) {
	statusCode := f.Mode.Args[0]
	intStatusCode, err := strconv.Atoi(statusCode)

	if err != nil {
		slog.Warn("Invalid status code", "statusCode", statusCode)
		return
	}

	slog.Debug("Injecting fault", "type", "HTTP Error", "statusCode", intStatusCode)

	if omit {
		slog.Debug("Forwarding response, before injecting fault")
		noOpWriter := &NoOpResponseWriter{}
		requestStart := time.Now()
		s.Proxy.ServeHTTP(noOpWriter, s.Request)
		s.DurationMs = time.Since(requestStart).Seconds() * 1000
	}

	headers := s.ResponseWriter.Header()

	isGrpc := s.Request.Header.Get("Content-Type") == "application/grpc"
	if isGrpc {
		headers.Set("Content-Type", "application/grpc")
		headers.Set("grpc-status", strconv.Itoa(toGrpcError(intStatusCode)))
		headers.Set("grpc-message", "Injected fault: GRPC HTTP error")
	} else {
		http.Error(s.ResponseWriter, "Injected fault: HTTP error", intStatusCode)
	}

	s.InjectedFault = &f
	s.ReponseOverwritten = true
}

func performDelay(f faultload.Fault, s *ProxyState) {
	delay := f.Mode.Args[0]
	intDelay, err := strconv.Atoi(delay)
	if err != nil {
		slog.Warn("Invalid delay", "delay", delay)
		return
	}

	duration := time.Duration(intDelay) * time.Millisecond
	time.Sleep(duration)
	s.InjectedFault = &f
}

func Perform(f faultload.Fault, s *ProxyState) {
	if f.Mode.Type == "HTTP_ERROR" {
		performHttpError(f, s, false)
		return
	} else if f.Mode.Type == "OMISSION_ERROR" {
		performHttpError(f, s, true)
		return
	} else if f.Mode.Type == "DELAY" {
		performDelay(f, s)
		return
	} else {
		slog.Warn("Unknown failure mode", "mode", f.Mode)
	}
}
