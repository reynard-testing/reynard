package proxy

import (
	"log"
	"net/http"
	"strconv"
	"time"

	"dflipse.nl/fit-proxy/faultload"
)

func performHttpError(f faultload.Fault, s *ProxyState) {
	statusCode := f.Args[0]
	intStatusCode, err := strconv.Atoi(statusCode)

	if err != nil {
		log.Printf("Invalid status code: %v\n", statusCode)
		return
	}

	log.Printf("Injecting fault: HTTP error %d\n", intStatusCode)

	isGrpc := s.Request.Header.Get("Content-Type") == "application/grpc"
	if isGrpc {
		s.ResponseWriter.Header().Set("Content-Type", "application/grpc")
		s.ResponseWriter.Header().Set("grpc-status", strconv.Itoa(toGrpcError(intStatusCode)))
		s.ResponseWriter.Header().Set("grpc-message", "Injected fault: GRPC HTTP error")
	} else {
		http.Error(s.ResponseWriter, "Injected fault: HTTP error", intStatusCode)
	}

	s.FaultInjected = true
	s.AppliedFault = &f
	s.ReponseOverwritten = true
}

func performDelay(f faultload.Fault, s *ProxyState) {
	delay := f.Args[0]
	intDelay, err := strconv.Atoi(delay)
	if err != nil {
		log.Printf("Invalid delay: %v\n", delay)
		return
	}

	duration := time.Duration(intDelay) * time.Millisecond
	time.Sleep(duration)
	s.FaultInjected = true
	s.AppliedFault = &f
}

func Perform(f faultload.Fault, s *ProxyState) {
	if f.FaultType == "HTTP_ERROR" {
		performHttpError(f, s)
		return
	} else if f.FaultType == "DELAY" {
		performDelay(f, s)
		return
	} else {
		log.Printf("Unknown fault type: %s\n", f.FaultType)
	}
}
