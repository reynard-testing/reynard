package tracing

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"os"

	"go.reynard.dev/instrumentation/controller/endpoints"
	"go.reynard.dev/instrumentation/shared/faultload"
	"go.reynard.dev/instrumentation/shared/trace"
	"go.reynard.dev/instrumentation/shared/util"
)

type RequestMetadata struct {
	TraceId        faultload.TraceID
	ParentId       faultload.SpanID
	ReportParentId faultload.SpanID
	SpanId         faultload.SpanID
	Protocol       string
	FaultUid       *faultload.FaultUid
	IsInitial      bool
}

var (
	queryHost        = os.Getenv("CONTROLLER_HOST")
	controllerClient = util.GetDefaultClient()
)

func postJSON(url string, body any) (*http.Response, error) {
	jsonBodyBytes, err := json.Marshal(body)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal JSON: %w", err)
	}

	req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonBodyBytes))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	return controllerClient.Do(req)
}

func attemptReport(report trace.TraceReport) bool {
	queryUrl := fmt.Sprintf("http://%s/v1/proxy/report", queryHost)
	resp, err := postJSON(queryUrl, report)

	if err != nil {
		slog.Warn("Failed to reach controller to report span ID", "spanId", report.SpanId, "error", err)
		return false
	}

	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		slog.Warn("Failed to reach controller to report span ID", "spanId", report.SpanId, "status", resp.Status)
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

// attemptIntercept returns registered=false only if the controller does not track the trace.
func attemptIntercept(req endpoints.InterceptRequest) (res *endpoints.InterceptResponse, registered bool) {
	queryUrl := fmt.Sprintf("http://%s/v2/proxy/intercept", queryHost)
	resp, err := postJSON(queryUrl, req)
	if err != nil {
		slog.Warn("Failed to reach controller to get UID", "error", err)
		return nil, true
	}

	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return nil, false
	}

	if resp.StatusCode != 200 {
		slog.Warn("Failed to reach controller to get UID", "status", resp.Status)
		return nil, true
	}

	var response endpoints.InterceptResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		slog.Warn("Failed to decode response from controller", "error", err)
		return nil, true
	}

	return &response, true
}

// GetUidAndFault asks the controller for the uid of the request and the fault to inject (nil if none).
// registered is false if the controller does not track the trace, so the request must not be touched.
func GetUidAndFault(metadata RequestMetadata, partial faultload.PartialInjectionPoint, includeEvents bool) (uid faultload.FaultUid, fault *faultload.Fault, registered bool) {
	req := endpoints.InterceptRequest{
		TraceId:             metadata.TraceId,
		ReportParentId:      metadata.ReportParentId,
		SpanId:              metadata.SpanId,
		IsInitial:           metadata.IsInitial,
		PartialPoint:        partial,
		IncludePredecessors: includeEvents,
	}

	res, registered := attemptIntercept(req)
	if registered && res == nil {
		// retry once
		res, registered = attemptIntercept(req)
	}

	if !registered {
		return faultload.FaultUid{}, nil, false
	}

	if res == nil {
		slog.Warn("Failed to get UID from controller after retry.", "traceId", metadata.TraceId, "parentId", metadata.ReportParentId)

		return faultload.FaultUid{
			Stack: []*faultload.InjectionPoint{},
		}, nil, true
	}

	return res.Uid, res.Fault, true
}
