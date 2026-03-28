package main

import (
	"bytes"
	"context"
	"encoding/base64"
	"io"
	"log"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/http/httputil"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"

	proxyPkg "go.reynard.dev/instrumentation/proxy/proxy"
	"go.reynard.dev/instrumentation/proxy/tracing"
	"go.reynard.dev/instrumentation/shared/faultload"
	"go.reynard.dev/instrumentation/shared/trace"
	"go.reynard.dev/instrumentation/shared/util"
)

const xrayTraceHeader = "X-Amzn-Trace-Id"

var (
	proxyTarget  string
	destination  string
	reverseProxy *httputil.ReverseProxy
)

func init() {
	logLevel := util.GetLogLevel()
	handler := slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: logLevel})
	slog.SetDefault(slog.New(handler))
	log.SetFlags(log.LstdFlags | log.Lmicroseconds)

	proxyTarget = os.Getenv("PROXY_TARGET")
	proxyTarget = strings.TrimSuffix(proxyTarget, "/")

	destination = os.Getenv("SERVICE_NAME")
	if destination == "" {
		destination = os.Getenv("OTEL_SERVICE_NAME")
	}

	targetURL, err := url.Parse(proxyTarget)
	if err != nil {
		log.Fatalf("Failed to parse PROXY_TARGET: %v", err)
	}

	reverseProxy = httputil.NewSingleHostReverseProxy(targetURL)
	reverseProxy.Transport = util.GetDefaultTransport()
	reverseProxy.ModifyResponse = func(resp *http.Response) error {
		if resp.Header.Get("content-type") == "" {
			resp.Header.Set("content-type", "application/octet-stream")
		}
		return nil
	}
}

// xrayContext holds the parsed components of an X-Amzn-Trace-Id header.
type xrayContext struct {
	rawRoot string
	traceId faultload.TraceID
	spanId  faultload.SpanID
	sampled bool
}

// parseXRayHeader parses the X-Amzn-Trace-Id header value.
// Format: Root=1-XXXXXXXX-YYYYYYYYYYYYYYYYYYYYYYYY;Parent=ZZZZZZZZZZZZZZZZ;Sampled=N
// The XRay Root is converted to a 32-char hex OTEL-compatible trace ID by
// concatenating the timestamp and unique ID segments (stripping the version prefix).
func parseXRayHeader(header string) *xrayContext {
	if header == "" {
		return nil
	}

	ctx := &xrayContext{}
	for _, part := range strings.Split(header, ";") {
		kv := strings.SplitN(strings.TrimSpace(part), "=", 2)
		if len(kv) != 2 {
			continue
		}
		switch kv[0] {
		case "Root":
			ctx.rawRoot = kv[1]
			// "1-XXXXXXXX-YYYYYYYYYYYYYYYYYYYYYYYY" → "XXXXXXXXYYYYYYYYYYYYYYYYYY" (32 hex chars)
			rp := strings.SplitN(kv[1], "-", 3)
			if len(rp) == 3 {
				ctx.traceId = faultload.TraceID(rp[1] + rp[2])
			}
		case "Parent":
			ctx.spanId = faultload.SpanID(kv[1])
		case "Sampled":
			ctx.sampled = kv[1] == "1"
		}
	}

	if ctx.traceId == "" {
		return nil
	}
	return ctx
}

// rebuildXRayHeader reconstructs the X-Amzn-Trace-Id header with a new parent span ID.
func rebuildXRayHeader(x *xrayContext, newSpan faultload.SpanID) string {
	sampled := "0"
	if x.sampled {
		sampled = "1"
	}
	return "Root=" + x.rawRoot + ";Parent=" + string(newSpan) + ";Sampled=" + sampled
}

func handleRequest(ctx context.Context, event events.APIGatewayV2HTTPRequest) (events.APIGatewayV2HTTPResponse, error) {
	fullStart := time.Now()

	// Build the request URL
	reqURL := proxyTarget + event.RawPath
	if event.RawQueryString != "" {
		reqURL += "?" + event.RawQueryString
	}

	// Decode body (API Gateway may base64-encode binary payloads)
	var body []byte
	if event.IsBase64Encoded {
		var err error
		body, err = base64.StdEncoding.DecodeString(event.Body)
		if err != nil {
			slog.Warn("Failed to decode base64 body, using raw", "error", err)
			body = []byte(event.Body)
		}
	} else {
		body = []byte(event.Body)
	}

	req, err := http.NewRequestWithContext(ctx, event.RequestContext.HTTP.Method, reqURL, bytes.NewReader(body))
	if err != nil {
		slog.Error("Failed to create request", "error", err)
		return events.APIGatewayV2HTTPResponse{StatusCode: http.StatusInternalServerError}, err
	}

	// Copy headers from the event (API Gateway v2 sends lowercase header keys)
	for k, v := range event.Headers {
		req.Header.Set(k, v)
	}

	// --- Extract tracing context ---
	// Prefer W3C traceparent; fall back to XRay.
	traceparent := req.Header.Get(proxyPkg.OTEL_PARENT_HEADER)
	tracestate := req.Header.Get(proxyPkg.OTEL_STATE_HEADER)

	xrayRaw := req.Header.Get(xrayTraceHeader)
	if xrayRaw == "" {
		// Lambda also sets the XRay trace ID as an environment variable
		xrayRaw = os.Getenv("_X_AMZN_TRACE_ID")
	}

	parentSpan := tracing.ParseTraceParent(traceparent)
	xray := parseXRayHeader(xrayRaw)

	var traceId faultload.TraceID
	var parentSpanId faultload.SpanID

	if parentSpan != nil {
		traceId = parentSpan.TraceID
		parentSpanId = parentSpan.ParentID
		slog.Debug("Extracted W3C trace context", "traceId", traceId, "parentSpanId", parentSpanId)
	} else if xray != nil {
		traceId = xray.traceId
		parentSpanId = xray.spanId
		slog.Debug("Extracted XRay trace context", "traceId", traceId, "parentSpanId", parentSpanId)
	}

	// No trace context: forward without inspection
	if traceId == "" {
		slog.Debug("No trace context, forwarding directly")
		return forward(req)
	}

	// Parse tracestate for FIT flags
	state := tracing.ParseTraceState(tracestate)
	slog.Debug("Found tracestate", "state", state)
	if state.GetWithDefault(proxyPkg.FIT_FLAG, "0") != "1" {
		slog.Debug("FIT not enabled, forwarding directly")
		return forward(req)
	}

	slog.Debug("Injectable request", "method", req.Method, "url", req.URL)

	// Generate a new span ID for this proxy hop
	newSpanId := tracing.NewSpanID()

	// Update outgoing tracing headers.
	// For W3C, keep non-canonical (lowercase) form to match the regular proxy behaviour.
	if parentSpan != nil {
		newTP := &tracing.TraceParentData{
			Version:    parentSpan.Version,
			TraceID:    parentSpan.TraceID,
			ParentID:   newSpanId,
			TraceFlags: parentSpan.TraceFlags,
		}
		req.Header.Del(proxyPkg.OTEL_PARENT_HEADER)
		req.Header[proxyPkg.OTEL_PARENT_HEADER] = []string{newTP.String()}
		req.Header.Del(proxyPkg.OTEL_STATE_HEADER)
		req.Header[proxyPkg.OTEL_STATE_HEADER] = []string{tracestate}
	}
	// Always update the XRay header so downstream Lambda spans nest correctly
	if xray != nil {
		req.Header.Set(xrayTraceHeader, rebuildXRayHeader(xray, newSpanId))
	}

	shouldMaskPayload := state.GetWithDefault(proxyPkg.FIT_MASK_PAYLOAD_FLAG, "0") == "1"
	shouldHashBody := state.GetWithDefault(proxyPkg.FIT_HASH_BODY_FLAG, "0") == "1"
	shouldUsePredecessors := state.GetWithDefault(proxyPkg.FIT_USE_PREDECESSORS, "0") == "1"
	shouldLogHeaders := state.GetWithDefault(proxyPkg.FIT_HEADER_LOGGING_FLAG, "0") == "1"

	isInitial := state.GetWithDefault(proxyPkg.FIT_IS_INITIAL_KEY, "0") == "1"
	if isInitial {
		slog.Debug("Is initial request")
		state.Delete(proxyPkg.FIT_IS_INITIAL_KEY)
		req.Header.Del(proxyPkg.OTEL_STATE_HEADER)
		req.Header[proxyPkg.OTEL_STATE_HEADER] = []string{state.String()}
	}

	reportParentId := faultload.SpanID(state.GetWithDefault(proxyPkg.FIT_PARENT_KEY, "0"))
	protocol := util.GetProtocol(req)

	if shouldLogHeaders {
		slog.Info("Request headers", "headers", req.Header)
	}

	metadata := tracing.RequestMetadata{
		TraceId:        traceId,
		ReportParentId: reportParentId,
		ParentId:       parentSpanId,
		SpanId:         newSpanId,
		IsInitial:      isInitial,
		Protocol:       protocol,
		FaultUid:       nil,
	}

	partialPoint := faultload.PartialPointFromRequest(req, destination, shouldMaskPayload)
	slog.Debug("Partial point", "partialPoint", partialPoint)

	requestFaultUid, fault := tracing.GetUidAndFaultFromController(metadata, partialPoint, shouldUsePredecessors)
	metadata.FaultUid = &requestFaultUid
	slog.Debug("Fault UID", "faultUid", requestFaultUid, "fault", fault)
	tracing.TrackFault(traceId, &requestFaultUid)

	// Update tracestate with the new parent span for downstream hops
	state.Set(proxyPkg.FIT_PARENT_KEY, string(newSpanId))
	req.Header.Del(proxyPkg.OTEL_STATE_HEADER)
	req.Header[proxyPkg.OTEL_STATE_HEADER] = []string{state.String()}

	// Set up response capture backed by a recorder we can read from
	recorder := httptest.NewRecorder()
	directlyForward := !shouldUsePredecessors
	capture := proxyPkg.NewResponseCapture(recorder, directlyForward)

	proxyState := proxyPkg.ProxyState{
		Proxy:          reverseProxy,
		ResponseWriter: capture,
		Request:        req,
	}

	if fault != nil {
		slog.Debug("Performing fault", "fault", fault)
		proxyPkg.Perform(*fault, &proxyState)
	}

	if !proxyState.ReponseOverwritten {
		requestStart := time.Now()
		reverseProxy.ServeHTTP(capture, req)
		proxyState.DurationMs = time.Since(requestStart).Seconds() * 1000
		slog.Debug("Response forwarded")
	}

	proxyState.ConcurrentFaults = tracing.GetTrackedAndClear(traceId, &requestFaultUid)
	proxyState.OverheadDurationMs = time.Since(fullStart).Seconds()*1000 - proxyState.DurationMs

	if !directlyForward {
		if err := capture.Flush(shouldLogHeaders); err != nil {
			slog.Error("Error flushing response", "error", err)
		}
	}

	// Report the span to the FIT controller
	respData := capture.GetResponseData(shouldHashBody)
	respData.DurationMs = proxyState.DurationMs
	respData.OverheadDurationMs = proxyState.OverheadDurationMs

	tracing.ReportSpanUID(trace.TraceReport{
		TraceId:       metadata.TraceId,
		SpanId:        metadata.SpanId,
		FaultUid:      *metadata.FaultUid,
		IsInitial:     metadata.IsInitial,
		Protocol:      metadata.Protocol,
		InjectedFault: proxyState.InjectedFault,
		Response:      &respData,
		ConcurrentTo:  proxyState.ConcurrentFaults,
	})

	return toLambdaResponse(recorder), nil
}

// forward passes the request through the reverse proxy without FIT inspection.
func forward(req *http.Request) (events.APIGatewayV2HTTPResponse, error) {
	recorder := httptest.NewRecorder()
	reverseProxy.ServeHTTP(recorder, req)
	return toLambdaResponse(recorder), nil
}

// toLambdaResponse converts a recorder into an APIGatewayV2HTTPResponse.
func toLambdaResponse(rec *httptest.ResponseRecorder) events.APIGatewayV2HTTPResponse {
	resp := rec.Result()
	defer resp.Body.Close()

	headers := make(map[string]string, len(resp.Header))
	for k, vv := range resp.Header {
		headers[k] = strings.Join(vv, ",")
	}

	respBody, _ := io.ReadAll(resp.Body)
	return events.APIGatewayV2HTTPResponse{
		StatusCode: resp.StatusCode,
		Headers:    headers,
		Body:       string(respBody),
	}
}

func main() {
	slog.Info("Starting Lambda proxy", "target", proxyTarget, "destination", destination)
	lambda.Start(handleRequest)
}
