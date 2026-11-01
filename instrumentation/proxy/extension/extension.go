// Package extension lets the proxy run as an AWS Lambda extension layer,
// next to the function's app (Lambda Web Adapter -> proxy -> app).
package extension

import (
	"encoding/json"
	"fmt"
	"log"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

// Run registers with the Lambda Extensions API and returns once Lambda shuts the environment down.
// Lambda only finishes its init phase once every extension waits on the next event.
func Run(runtimeApi string) {
	baseUrl := fmt.Sprintf("http://%s/2020-01-01/extension", runtimeApi)

	req, _ := http.NewRequest("POST", baseUrl+"/register", strings.NewReader(`{"events":["SHUTDOWN"]}`))
	// Lambda requires the name to match the executable's file name in /opt/extensions
	req.Header.Set("Lambda-Extension-Name", filepath.Base(os.Args[0]))
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		log.Fatalf("Failed to register Lambda extension: %v\n", err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		log.Fatalf("Failed to register Lambda extension: %s\n", resp.Status)
	}

	id := resp.Header.Get("Lambda-Extension-Identifier")
	slog.Info("Registered as Lambda extension", "id", id)

	for {
		// Long-polls until the next event, so the default client (no timeout) is intended
		req, _ := http.NewRequest("GET", baseUrl+"/event/next", nil)
		req.Header.Set("Lambda-Extension-Identifier", id)
		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			log.Fatalf("Failed to get next Lambda event: %v\n", err)
		}

		var event struct {
			EventType string `json:"eventType"`
		}
		err = json.NewDecoder(resp.Body).Decode(&event)
		resp.Body.Close()
		if err != nil {
			log.Fatalf("Failed to decode Lambda event: %v\n", err)
		}

		if event.EventType == "SHUTDOWN" {
			slog.Info("Lambda is shutting down")
			return
		}
	}
}
