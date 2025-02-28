package control

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strconv"

	"dflipse.nl/fit-proxy/config"
	"dflipse.nl/fit-proxy/faultload"
)

func StartControlServer(config config.ControlConfig) {
	controlPort := ":" + strconv.Itoa(config.Port)
	http.HandleFunc("/v1/faultload/register", registerFaultloadHandler)
	http.HandleFunc("/v1/faultload/unregister", unregisterFaultloadHandler)

	err := http.ListenAndServe(controlPort, nil)
	log.Printf("Listening for control commands on port %s\n", controlPort)

	if err != nil {
		log.Fatalf("Error starting register faultload server: %v\n", err)
	}
}

// Handle the /v1/faultload/register endpoint
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
	// myDestination := tracing.GetHostIdentifier(r.Host)

	log.Printf("\n----------------------------\n")
	log.Printf("Registering faultload (size=%d) for trace ID %s\n", len(newFaultload.Faults), newFaultload.TraceId)
	for _, fault := range faults {
		// TODO: Filter faults based on destination
		// We don't know our destination yet, so we can't filter
		// if fault.Uid.Destination == myDestination {
		myFaults = append(myFaults, fault)
		// }
	}

	log.Printf("Registered %d faults for trace ID %s\n", len(myFaults), newFaultload.TraceId)
	// Store the faultload for the given trace ID
	RegisteredFaults.Register(newFaultload.TraceId, myFaults)

	// Respond with a 200 OK
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, "OK")
}

type UnregisterFaultloadRequest struct {
	TraceId string `json:"trace_id"`
}

// Handle the /v1/faultload/unregister endpoint
func unregisterFaultloadHandler(w http.ResponseWriter, r *http.Request) {
	// Parse the newFaultload from the request body
	var requestData UnregisterFaultloadRequest
	err := json.NewDecoder(r.Body).Decode(&requestData)

	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(w, "Failed to parse request body: %v", err)
		return
	}

	log.Printf("Removed faults for trace ID %s\n", requestData.TraceId)
	// Store the faultload for the given trace ID
	RegisteredFaults.Remove(requestData.TraceId)

	// Respond with a 200 OK
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, "OK")
}
