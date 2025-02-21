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
	SpanId        string             `json:"span_id"`
	FaultUid      faultload.FaultUid `json:"uid"`
	InjectedFault *faultload.Fault   `json:"injected_fault"`
	Response      *ResponseData      `json:"response"`
}

type RequestMetadata struct {
	SpanId   string
	FaultUid faultload.FaultUid
}

var queryHost string = os.Getenv("ORCHESTRATOR_HOST")

func ReportSpanUID(report RequestReport) {
	queryUrl := fmt.Sprintf("http://%s/v1/link", queryHost)

	jsonBodyBytes, err := json.Marshal(report)
	if err != nil {
		log.Printf("Failed to marshal JSON: %v\n", err)
		return
	}

	resp, err := http.Post(queryUrl, "application/json", bytes.NewBuffer(jsonBodyBytes))

	if err != nil {
		log.Printf("Failed to report span ID: %v\n", err)
		return
	}

	defer resp.Body.Close()
}
