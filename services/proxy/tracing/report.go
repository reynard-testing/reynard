package tracing

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
)

type ResponseData struct {
	Status int    `json:"status"`
	Body   string `json:"body"`
}

type RequestReport struct {
	SpanID        string       `json:"span_id"`
	SpanUID       string       `json:"span_uid"`
	FaultInjected bool         `json:"fault_injected"`
	Response      ResponseData `json:"response"`
}

type RequestMetadata struct {
	SpanID  string
	SpanUID string
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
