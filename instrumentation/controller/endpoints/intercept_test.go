package endpoints

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"go.reynard.dev/instrumentation/controller/store"
	"go.reynard.dev/instrumentation/shared/faultload"
)

func postIntercept(t *testing.T, req InterceptRequest) *httptest.ResponseRecorder {
	t.Helper()
	body, err := json.Marshal(req)
	if err != nil {
		t.Fatal(err)
	}
	rec := httptest.NewRecorder()
	InterceptHandler(rec, httptest.NewRequest(http.MethodPost, "/v2/proxy/intercept", bytes.NewReader(body)))
	return rec
}

func initialRequest(traceId faultload.TraceID, spanId faultload.SpanID, destination string) InterceptRequest {
	return InterceptRequest{
		TraceId:      traceId,
		SpanId:       spanId,
		IsInitial:    true,
		PartialPoint: faultload.PartialInjectionPoint{Destination: destination, Signature: "GET /"},
	}
}

func TestInterceptUnregisteredTraceIsNotTracked(t *testing.T) {
	store.TraceFaults.Clear()
	store.Reports.Clear()

	rec := postIntercept(t, initialRequest("unregistered", "s1", "a"))

	if rec.Code != http.StatusNotFound {
		t.Fatalf("expected 404 for an unregistered trace, got %d", rec.Code)
	}
	if store.Reports.HasTraceId("unregistered") {
		t.Error("no report should be stored for an unregistered trace")
	}
}

func TestInterceptReturnsMatchingFault(t *testing.T) {
	store.TraceFaults.Clear()
	store.Reports.Clear()

	destination := "a"
	signature := "GET /"
	mode := faultload.FaultMode{Type: "HTTP_ERROR", Args: []string{"503"}}
	store.TraceFaults.Register("t1", []faultload.Fault{{
		Uid:  faultload.FaultUid{Stack: []*faultload.InjectionPoint{{Destination: &destination, Signature: &signature, Count: 0}}},
		Mode: mode,
	}})

	cases := []struct {
		destination string
		wantFault   bool
	}{
		{"a", true},
		{"b", false},
	}

	for i, c := range cases {
		rec := postIntercept(t, initialRequest("t1", faultload.SpanID(rune('1'+i)), c.destination))
		if rec.Code != http.StatusOK {
			t.Fatalf("destination %s: expected 200, got %d", c.destination, rec.Code)
		}

		var res InterceptResponse
		if err := json.NewDecoder(rec.Body).Decode(&res); err != nil {
			t.Fatal(err)
		}
		if *res.Uid.Point().Destination != c.destination {
			t.Errorf("destination %s: uid has destination %s", c.destination, *res.Uid.Point().Destination)
		}
		if (res.Fault != nil) != c.wantFault {
			t.Errorf("destination %s: got fault %v, want fault: %v", c.destination, res.Fault, c.wantFault)
		}
		if res.Fault != nil && res.Fault.Mode.Args[0] != "503" {
			t.Errorf("destination %s: got mode %v, want %v", c.destination, res.Fault.Mode, mode)
		}
	}

	if len(store.Reports.GetByTraceId("t1")) != 2 {
		t.Error("each intercept request should store a report")
	}
}
