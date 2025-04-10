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

type UidRequest struct {
	TraceId        string `json:"trace_id"`
	ReportParentId string `json:"parent_span_id"`
	IsInitial      bool   `json:"is_initial"`
}

type RequestReport struct {
	TraceId       string                `json:"trace_id"`
	SpanId        string                `json:"span_id"`
	ParentId      string                `json:"parent_span_id"`
	FaultUid      faultload.FaultUid    `json:"uid"`
	IsInitial     bool                  `json:"is_initial"`
	InjectedFault *faultload.Fault      `json:"injected_fault"`
	Response      *ResponseData         `json:"response"`
	ConcurrentTo  []*faultload.FaultUid `json:"concurrent_to"`
}

type RequestMetadata struct {
	TraceId        string
	ParentId       string
	ReportParentId string
	SpanId         string
	FaultUid       faultload.FaultUid
	IsInitial      bool
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

func attemptGetUid(req UidRequest) *faultload.FaultUid {
	queryUrl := fmt.Sprintf("http://%s/v1/parent_uid", queryHost)

	jsonBodyBytes, err := json.Marshal(req)
	if err != nil {
		log.Printf("Failed to marshal JSON: %v\n", err)
		return nil
	}

	resp, err := http.Post(queryUrl, "application/json", bytes.NewBuffer(jsonBodyBytes))

	if err != nil {
		log.Printf("Failed to reach orchestrator to get UID: %v\n", err)
		return nil
	}

	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		log.Printf("Failed to get UID from orchestrator: %v\n", resp)
		return nil
	}

	var response faultload.FaultUid
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		log.Printf("Failed to decode response: %v\n", err)
		return nil
	}

	return &response
}

func GetUid(req UidRequest) faultload.FaultUid {
	res := attemptGetUid(req)

	if res == nil {
		// retry once
		res = attemptGetUid(req)
	}

	if res == nil {
		log.Printf("Failed to get UID from orchestrator after retry.\n")
		return faultload.FaultUid{
			Stack: []faultload.InjectionPoint{},
		}
	} else {
		return *res
	}
}
