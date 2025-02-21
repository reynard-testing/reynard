package control

import (
	"fmt"
	"log"
	"net/http"
	"strconv"

	"dflipse.nl/fit-proxy/config"
	"dflipse.nl/fit-proxy/faultload"
)

var RegisteredFaults map[string][]faultload.Fault = make(map[string][]faultload.Fault)

func StartControlServer(config config.ControlConfig) {
	registerFaultloadPort := ":" + strconv.Itoa(config.Port)
	http.HandleFunc("/v1/register_faultload", registerFaultloadHandler)

	err := http.ListenAndServe(registerFaultloadPort, nil)
	log.Printf("Listening for control commands on port %s\n", registerFaultloadPort)

	if err != nil {
		log.Fatalf("Error starting register faultload server: %v\n", err)
	}
}

// Handle the /v1/register_faultload endpoint
func registerFaultloadHandler(w http.ResponseWriter, r *http.Request) {
	// Parse the faultload from the request body
	faults, traceId, err := faultload.ParseRequest(r)
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(w, "Failed to parse request body: %v", err)
		return
	}

	// Store the faultload for the given trace ID
	RegisteredFaults[traceId] = faults

	// Respond with a 200 OK
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, "OK")
}
