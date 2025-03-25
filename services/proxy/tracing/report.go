package tracing

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"

	"dflipse.nl/fit-proxy/faultload"
)

type ResponseData struct {
	Status int    `json:"status"`
	Body   string `json:"body"`
}

type RequestReport struct {
	TraceId       string             `json:"trace_id"`
	SpanId        string             `json:"span_id"`
	FaultUid      faultload.FaultUid `json:"uid"`
	IsInitial     bool               `json:"is_initial"`
	InjectedFault *faultload.Fault   `json:"injected_fault"`
	Response      *ResponseData      `json:"response"`
}

type RequestMetadata struct {
	TraceId   string
	SpanId    string
	FaultUid  faultload.FaultUid
	IsInitial bool
}

var queryHost string = os.Getenv("ORCHESTRATOR_HOST")

func attemptReport(report RequestReport) bool {

	queryUrl := fmt.Sprintf("http://%s/v1/link", queryHost)

	jsonBodyBytes, err := json.Marshal(report)
	if err != nil {
		log.Printf("Failed to marshal JSON: %v\n", err)
		return false
	}

	resp, err := http.Post(queryUrl, "application/json", bytes.NewBuffer(jsonBodyBytes))

	if err != nil {
		log.Printf("Failed to report span ID: %v\n", err)
		return false
	}

	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		log.Printf("Failed to report span ID: %v\n", resp)
		return false
	}

	return true

}

func ReportSpanUID(report RequestReport) bool {
	success := attemptReport(report)

	if !success {
		// retry once
		success = attemptReport(report)
	}

	return success
}
