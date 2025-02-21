package faultload

import (
	"encoding/json"
	"log"
	"net/http"
	"net/url"

	"dflipse.nl/fit-proxy/tracing"
)

func Parse(tracestate tracing.TraceStateData) []Fault {
	faultload := tracestate.GetWithDefault("faultload", "")
	decoded, err := url.QueryUnescape(faultload)

	if err != nil {
		log.Printf("Failed to decode fault: %v\n", err)
		return nil
	}

	return parseFaultload(decoded)
}

func parseFaultload(data string) []Fault {
	var faultData [][]string

	err := json.Unmarshal([]byte(data), &faultData)
	if err != nil {
		log.Printf("Failed to unmarshal fault: %v\n", err)
		return nil
	}

	var faults []Fault

	for _, fd := range faultData {
		if len(fd) < 3 {
			log.Printf("Invalid fault data: %v\n", fd)
			continue
		}

		faults = append(faults, Fault{
			SpanUID:   fd[0],
			FaultType: fd[1],
			Args:      fd[2:],
		})
	}

	return faults
}

func ParseRequest(r *http.Request) ([]Fault, string, error) {
	var requestBody struct {
		Faultload string `json:"faultload"`
		TraceId   string `json:"traceId"`
	}

	err := json.NewDecoder(r.Body).Decode(&requestBody)

	if err != nil {
		log.Printf("Failed to decode request body: %v\n", err)
		return nil, "", err
	}

	faults := parseFaultload(requestBody.Faultload)
	return faults, requestBody.TraceId, nil
}
