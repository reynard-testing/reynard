package control

import (
	"sync"

	"dflipse.nl/fit-proxy/faultload"
)

type FaultRegister struct {
	sync.RWMutex
	m map[string][]faultload.Fault
}

var RegisteredFaults = FaultRegister{m: make(map[string][]faultload.Fault)}

func (fr *FaultRegister) Register(traceId string, faults []faultload.Fault) {
	fr.Lock()
	defer fr.Unlock()
	fr.m[traceId] = faults
}

func (fr *FaultRegister) Get(traceId string) ([]faultload.Fault, bool) {
	fr.RLock()
	defer fr.RUnlock()
	faults, exists := fr.m[traceId]
	return faults, exists
}
