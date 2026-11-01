package store

import (
	"sync"

	"go.reynard.dev/instrumentation/shared/faultload"
)

// FaultRegister holds the faults to inject per registered trace.
// A trace is tracked if and only if it is registered here.
type FaultRegister struct {
	sync.RWMutex
	m map[faultload.TraceID][]faultload.Fault
}

var TraceFaults = FaultRegister{m: make(map[faultload.TraceID][]faultload.Fault)}

func (fr *FaultRegister) Register(traceId faultload.TraceID, faults []faultload.Fault) {
	fr.Lock()
	defer fr.Unlock()
	fr.m[traceId] = faults
}

func (fr *FaultRegister) IsTraceRegistered(traceId faultload.TraceID) bool {
	fr.RLock()
	defer fr.RUnlock()
	_, exists := fr.m[traceId]
	return exists
}

func (fr *FaultRegister) Remove(traceId faultload.TraceID) {
	fr.Lock()
	defer fr.Unlock()
	delete(fr.m, traceId)
}

// GetMatchingFault returns the first registered fault for the trace that matches uid, or nil.
func (fr *FaultRegister) GetMatchingFault(traceId faultload.TraceID, uid faultload.FaultUid) *faultload.Fault {
	fr.RLock()
	defer fr.RUnlock()

	for _, fault := range fr.m[traceId] {
		if fault.Uid.Matches(uid) {
			return &fault
		}
	}

	return nil
}

func (fr *FaultRegister) Clear() {
	fr.Lock()
	defer fr.Unlock()
	fr.m = make(map[faultload.TraceID][]faultload.Fault)
}
