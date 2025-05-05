package control

import (
	"sync"

	"dflipse.nl/ds-fit/shared/faultload"
)

type FaultRegister struct {
	sync.RWMutex
	m map[faultload.TraceID][]faultload.Fault
}

var RegisteredFaults = FaultRegister{m: make(map[faultload.TraceID][]faultload.Fault)}

func (fr *FaultRegister) Register(traceId faultload.TraceID, faults []faultload.Fault) {
	fr.Lock()
	defer fr.Unlock()
	fr.m[traceId] = faults
}

func (fr *FaultRegister) Remove(traceId faultload.TraceID) {
	fr.Lock()
	defer fr.Unlock()
	delete(fr.m, traceId)
}

func (fr *FaultRegister) Get(traceId faultload.TraceID) ([]faultload.Fault, bool) {
	fr.RLock()
	defer fr.RUnlock()
	faults, exists := fr.m[traceId]
	return faults, exists
}
