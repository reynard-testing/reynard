package proxy

import (
	"log"
	"net/http"
	"strconv"
	"time"

	"dflipse.nl/ds-fit/proxy/faultload"
)

func performHttpError(f faultload.Fault, s *ProxyState, omit bool) {
	statusCode := f.Mode.Args[0]
	intStatusCode, err := strconv.Atoi(statusCode)

	if err != nil {
		log.Printf("Invalid status code: %v\n", statusCode)
		return
	}

	log.Printf("Injecting fault: HTTP error %d\n", intStatusCode)

	if omit {
		log.Printf("Forwarding response, before injecting fault\n")
		noOpWriter := &NoOpResponseWriter{}
		s.Proxy.ServeHTTP(noOpWriter, s.Request)
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
		log.Printf("Invalid delay: %v\n", delay)
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
		log.Printf("Unknown fault type: %s\n", f.Mode)
	}
}
