package store

import (
	"sync"

	"go.reynard.dev/instrumentation/shared/faultload"
)

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

func (fr *FaultRegister) GetMatchingFault(traceId faultload.TraceID, uid faultload.FaultUid) *faultload.Fault {
	fr.RLock()
	defer fr.RUnlock()

	faults, exists := fr.m[traceId]
	if !exists {
		return nil
	}

	for _, fault := range faults {
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
