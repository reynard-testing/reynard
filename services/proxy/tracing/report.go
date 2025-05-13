package tracing

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"

	"dflipse.nl/ds-fit/controller/endpoints"
	"dflipse.nl/ds-fit/shared/faultload"
	"dflipse.nl/ds-fit/shared/trace"
)

type RequestMetadata struct {
	TraceId        faultload.TraceID
	ParentId       faultload.SpanID
	ReportParentId faultload.SpanID
	SpanId         faultload.SpanID
	FaultUid       faultload.FaultUid
	IsInitial      bool
}

var queryHost string = os.Getenv("CONTROLLER_HOST")

func attemptReport(report trace.TraceReport) bool {
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

func ReportSpanUID(report trace.TraceReport) bool {
	success := attemptReport(report)

	if !success {
		// retry once
		success = attemptReport(report)
	}

	return success
}

func attemptGetUid(req endpoints.UidRequest) *endpoints.UidResponse {
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

	var response endpoints.UidResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		log.Printf("Failed to decode response: %v\n", err)
		return nil
	}

	return &response
}

func GetUid(traceId faultload.TraceID, parentId faultload.SpanID, isInitial bool) (faultload.FaultUid, faultload.InjectionPointCallStack) {
	if isInitial {
		return faultload.FaultUid{
			Stack: []faultload.InjectionPoint{},
		}, faultload.InjectionPointCallStack{}
	}

	req := endpoints.UidRequest{
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
