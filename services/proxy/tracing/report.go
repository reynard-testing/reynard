package tracing

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"

	"dflipse.nl/ds-fit/shared/faultload"
)

type ResponseData struct {
	Status     int     `json:"status"`
	Body       string  `json:"body"`
	DurationMs float64 `json:"duration_ms"`
}

type UidRequest struct {
	TraceId        string `json:"trace_id"`
	ReportParentId string `json:"parent_span_id"`
}
type UidResponse struct {
	Stack           []faultload.InjectionPoint        `json:"stack"`
	CompletedEvents faultload.InjectionPointCallStack `json:"completed_events"`
}

type RequestReport struct {
	TraceId       string                `json:"trace_id"`
	SpanId        string                `json:"span_id"`
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

var queryHost string = os.Getenv("CONTROLLER_HOST")

func attemptReport(report RequestReport) bool {

	queryUrl := fmt.Sprintf("http://%s/v1/proxy/report", queryHost)

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

func attemptGetUid(req UidRequest) *UidResponse {
	queryUrl := fmt.Sprintf("http://%s/v1/proxy/get-parent-uid", queryHost)

	jsonBodyBytes, err := json.Marshal(req)
	if err != nil {
		log.Printf("Failed to marshal JSON: %v\n", err)
		return nil
	}

	resp, err := http.Post(queryUrl, "application/json", bytes.NewBuffer(jsonBodyBytes))

	if err != nil {
		log.Printf("Failed to reach controller to get UID: %v\n", err)
		return nil
	}

	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		log.Printf("Failed to get UID from controller: %v\n", resp)
		return nil
	}

	var response UidResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		log.Printf("Failed to decode response: %v\n", err)
		return nil
	}

	return &response
}

func GetUid(traceId, parentId string, isInitial bool) (faultload.FaultUid, faultload.InjectionPointCallStack) {
	if isInitial {
		return faultload.FaultUid{
			Stack: []faultload.InjectionPoint{},
		}, faultload.InjectionPointCallStack{}
	}

	req := UidRequest{
		TraceId:        traceId,
		ReportParentId: parentId,
	}

	res := attemptGetUid(req)

	if res == nil {
		// retry once
		res = attemptGetUid(req)
	}

	if res == nil {
		log.Printf("Failed to get UID from controller after retry.\n")
		return faultload.FaultUid{
			Stack: []faultload.InjectionPoint{},
		}, faultload.InjectionPointCallStack{}
	}

	return faultload.FaultUid{
		Stack: res.Stack,
	}, res.CompletedEvents
}
