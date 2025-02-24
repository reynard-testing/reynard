package control

import (
	"fmt"
	"log"
	"net/http"
	"strconv"

	"dflipse.nl/fit-proxy/config"
	"dflipse.nl/fit-proxy/faultload"
	"dflipse.nl/fit-proxy/tracing"
)

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
	// Parse the newFaultload from the request body
	newFaultload, err := faultload.ParseRequest(r)
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(w, "Failed to parse request body: %v", err)
		return
	}

	faults := newFaultload.Faults
	myFaults := []faultload.Fault{}

	log.Printf("\n----------------------------\n")
	log.Printf("Registering faultload (size=%d) for trace ID %s\n", len(newFaultload.Faults), newFaultload.TraceId)
	for _, fault := range faults {
		if fault.Uid.Destination == tracing.ServiceName {
			myFaults = append(myFaults, fault)
		}
	}

	log.Printf("Registered %d faults for trace ID %s\n", len(myFaults), newFaultload.TraceId)
	// Store the faultload for the given trace ID
	RegisteredFaults.Register(newFaultload.TraceId, myFaults)

	// Respond with a 200 OK
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, "OK")
}
