package endpoints

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"

	"dflipse.nl/ds-fit/controller/store"
	"dflipse.nl/ds-fit/shared/faultload"
	"dflipse.nl/ds-fit/shared/util"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
)

var (
	ProxyList       = strings.Split(os.Getenv("PROXY_LIST"), ",")
	ProxyRetryCount = util.GetIntEnvOrDefault("PROXY_RETRY_COUNT", 3)
	client          = http.Client{
		Transport: otelhttp.NewTransport(http.DefaultTransport),
	}
)

func RegisterFaultload(proxyAddr string, ctx context.Context, f *faultload.Faultload) error {
	url := "http://" + proxyAddr + "/v1/faultload/register"
	body, err := json.Marshal(f)
	if err != nil {
		return fmt.Errorf("failed to marshal faultload: %v", err)
	}

	// Send the request — this will create and export a span
	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(body))
	if err != nil {
		return fmt.Errorf("failed to create POST request: %v", err)
	}

	resp, err := client.Do(req)

	if err != nil {
		return fmt.Errorf("failed to perform POST request: %v", err)
	}

	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("failed to register faultload at proxy %s: %s", proxyAddr, resp.Status)
	}

	log.Printf("Registered faultload at proxy %s\n", proxyAddr)

	return nil
}

func retry(attempts int, sleep time.Duration, f func() error) (err error) {
	for i := 0; i < attempts; i++ {
		if i > 0 {
			log.Println("retrying after error:", err)
			time.Sleep(sleep)
			sleep *= 2
		}

		err = f()

		if err == nil {
			return nil
		}
	}

	return fmt.Errorf("after %d attempts, last error: %s", attempts, err)
}

func RegisterFaultloadsAtProxies(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	// Parse the request body to get the Faultload
	faultload, err := faultload.ParseRequest(r)
	if err != nil {
		http.Error(w, "Failed to parse request", http.StatusBadRequest)
		return
	}

	store.TraceIds.Register(faultload.TraceId)

	// Register the Faultload at the proxies
	var wg sync.WaitGroup
	errChan := make(chan error, len(ProxyList))

	for _, proxy := range ProxyList {
		wg.Add(1)

		go func(proxy string) {
			defer wg.Done()
			err := retry(ProxyRetryCount, time.Second, func() error {
				return RegisterFaultload(proxy, ctx, faultload)
			})
			if err != nil {
				errChan <- fmt.Errorf("failed to register faultload at proxy %s: %v", proxy, err)
			}
		}(proxy)
	}

	wg.Wait()
	close(errChan)

	if len(errChan) > 0 {
		http.Error(w, "Failed to register faultload at one or more proxies", http.StatusInternalServerError)
		return
	}

	log.Printf("Registered faultload (size=%d) for trace ID %s\n", len(faultload.Faults), faultload.TraceId)

	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}

type UnregisterFaultloadRequest struct {
	TraceId faultload.TraceID `json:"trace_id"`
}

func UnregisterFaultload(proxyAddr string, ctx context.Context, payload *UnregisterFaultloadRequest) error {
	url := "http://" + proxyAddr + "/v1/faultload/register"
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("failed to marshal faultload: %v", err)
	}

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(body))
	if err != nil {
		return fmt.Errorf("failed to create POST request: %v", err)
	}

	resp, err := client.Do(req)

	if err != nil {
		return fmt.Errorf("failed to perform POST request: %v", err)
	}

	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("failed to register faultload at proxy %s: %s", proxyAddr, resp.Status)
	}

	log.Printf("Unregistered faultload at proxy %s\n", proxyAddr)

	return nil
}

func UnregisterFaultloadsAtProxies(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	// Parse the request body to get the Faultload
	var requestData UnregisterFaultloadRequest
	err := json.NewDecoder(r.Body).Decode(&requestData)
	if err != nil {
		http.Error(w, "Failed to parse request", http.StatusBadRequest)
		return
	}

	store.TraceIds.Unregister(requestData.TraceId)

	// Register the Faultload at the proxies
	var wg sync.WaitGroup
	errChan := make(chan error, len(ProxyList))

	for _, proxy := range ProxyList {
		wg.Add(1)

		go func(proxy string) {
			defer wg.Done()
			err := retry(ProxyRetryCount, time.Second, func() error {
				return UnregisterFaultload(proxy, ctx, &requestData)
			})
			if err != nil {
				errChan <- fmt.Errorf("failed to unregister faultload at proxy %s: %v", proxy, err)
			}
		}(proxy)
	}

	wg.Wait()
	close(errChan)

	if len(errChan) > 0 {
		http.Error(w, "Failed to unregister faultload at one or more proxies", http.StatusInternalServerError)
		return
	}

	log.Printf("Unregistered faults for trace ID %s\n", requestData.TraceId)

	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}
