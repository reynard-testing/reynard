package faultload

import (
	"encoding/json"
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strconv"
	"time"

	"dflipse.nl/fit-proxy/tracing"
)

type Fault struct {
	SpanUID   string
	FaultType string
	Args      []string
}

func Parse(tracestate tracing.TraceStateData) []Fault {
	faultload := tracestate.GetWithDefault("faultload", "")
	decoded, err := url.QueryUnescape(faultload)

	if err != nil {
		log.Printf("Failed to decode fault: %v\n", err)
		return nil
	}

	return parseFaultload(decoded)
}

func parseFaultload(data string) []Fault {
	var faultData [][]string

	err := json.Unmarshal([]byte(data), &faultData)
	if err != nil {
		log.Printf("Failed to unmarshal fault: %v\n", err)
		return nil
	}

	var faults []Fault

	for _, fd := range faultData {
		if len(fd) < 3 {
			log.Printf("Invalid fault data: %v\n", fd)
			continue
		}

		faults = append(faults, Fault{
			SpanUID:   fd[0],
			FaultType: fd[1],
			Args:      fd[2:],
		})
	}

	return faults
}

func ParseRequest(r *http.Request) ([]Fault, string, error) {
	var requestBody struct {
		Faultload string `json:"faultload"`
		TraceId   string `json:"traceId"`
	}

	err := json.NewDecoder(r.Body).Decode(&requestBody)

	if err != nil {
		log.Printf("Failed to decode request body: %v\n", err)
		return nil, "", err
	}

	faults := parseFaultload(requestBody.Faultload)
	return faults, requestBody.TraceId, nil
}

func (f Fault) performHttpError(w http.ResponseWriter) bool {
	statusCode := f.Args[0]
	intStatusCode, err := strconv.Atoi(statusCode)

	if err != nil {
		log.Printf("Invalid status code: %v\n", statusCode)
		return false
	}

	log.Printf("Injecting fault: HTTP error %d\n", intStatusCode)
	http.Error(w, "Injected fault: HTTP error", intStatusCode)
	return true
}

func (f Fault) performDelay(p *httputil.ReverseProxy, w http.ResponseWriter, r *http.Request) bool {
	delay := f.Args[0]
	intDelay, err := strconv.Atoi(delay)
	if err != nil {
		log.Printf("Invalid delay: %v\n", delay)
		return false
	}

	duration := time.Duration(intDelay) * time.Millisecond
	time.Sleep(duration)

	p.ServeHTTP(w, r)
	return true
}

func (f Fault) Perform(p *httputil.ReverseProxy, w http.ResponseWriter, r *http.Request) bool {
	if f.FaultType == "HTTP_ERROR" {
		return f.performHttpError(w)
	} else if f.FaultType == "DELAY" {
		return f.performDelay(p, w, r)
	} else {
		log.Printf("Unknown fault type: %s\n", f.FaultType)
	}

	return false
}
