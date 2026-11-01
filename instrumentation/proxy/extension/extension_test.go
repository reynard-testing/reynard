package extension

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestRunRegistersAndStopsOnShutdown(t *testing.T) {
	events := []string{"INVOKE", "SHUTDOWN"}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/2020-01-01/extension/register":
			if got := r.Header.Get("Lambda-Extension-Name"); got != filepath.Base(os.Args[0]) {
				t.Errorf("unexpected extension name %q", got)
			}
			w.Header().Set("Lambda-Extension-Identifier", "ext-id")
		case "/2020-01-01/extension/event/next":
			if got := r.Header.Get("Lambda-Extension-Identifier"); got != "ext-id" {
				t.Errorf("unexpected extension id %q", got)
			}
			w.Write([]byte(`{"eventType":"` + events[0] + `"}`))
			events = events[1:]
		default:
			t.Errorf("unexpected path %s", r.URL.Path)
		}
	}))
	defer server.Close()

	Run(strings.TrimPrefix(server.URL, "http://"))

	if len(events) != 0 {
		t.Errorf("expected all events to be consumed, %d left", len(events))
	}
}
