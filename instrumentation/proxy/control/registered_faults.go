package control

import (
	"sync"

	"go.reynard.dev/instrumentation/shared/faultload"
)

type FaultRegister struct {
	sync.RWMutex
	m            map[faultload.TraceID][]faultload.Fault
	globalFaults []faultload.Fault
}

var RegisteredFaults = FaultRegister{
	m:            make(map[faultload.TraceID][]faultload.Fault),
	globalFaults: make([]faultload.Fault, 0),
}

func (fr *FaultRegister) Register(traceId faultload.TraceID, faults []faultload.Fault) {
	fr.Lock()
	defer fr.Unlock()
	fr.m[traceId] = faults
}

func (fr *FaultRegister) RegisterGlobal(faults []faultload.Fault) {
	fr.Lock()
	defer fr.Unlock()
	fr.globalFaults = append(fr.globalFaults, faults...)
}

func (fr *FaultRegister) Remove(traceId faultload.TraceID) {
	fr.Lock()
	defer fr.Unlock()
	delete(fr.m, traceId)
}

func (fr *FaultRegister) RemoveGlobal() {
	fr.Lock()
	defer fr.Unlock()
	fr.globalFaults = make([]faultload.Fault, 0)
}

func (fr *FaultRegister) Get(traceId faultload.TraceID) ([]faultload.Fault, bool) {
	fr.RLock()
	defer fr.RUnlock()
	faults, exists := fr.m[traceId]

	// Always include global faults in the returned slice
	allFaults := append(fr.globalFaults, faults...)
	return allFaults, exists
}
