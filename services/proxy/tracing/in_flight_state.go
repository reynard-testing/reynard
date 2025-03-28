package tracing

import (
	"sync"

	"dflipse.nl/fit-proxy/faultload"
)

// Keep track of all in flight requests, on a per trace id basis
// This is used to determine which requests are concurrent to each other.
type InFlightTracker struct {
	sync.RWMutex
	m map[string]map[*faultload.FaultUid][]*faultload.FaultUid
}

var inFlightTracker = InFlightTracker{m: make(map[string]map[*faultload.FaultUid][]*faultload.FaultUid)}

func (tracker *InFlightTracker) Track(traceId string, uid *faultload.FaultUid) {
	tracker.Lock()
	defer tracker.Unlock()

	// Create a new entry for the trace id if it doesn't exist yet.
	if _, exists := tracker.m[traceId]; !exists {
		tracker.m[traceId] = make(map[*faultload.FaultUid][]*faultload.FaultUid)
	}

	// Create a list of currently in flight uids.
	list := make([]*faultload.FaultUid, 0)

	// Add the new uid to all existing in flight uids.
	for key := range tracker.m[traceId] {
		tracker.m[traceId][key] = append(tracker.m[traceId][key], uid)
		list = append(list, key)
	}

	// Add the new uid to the tracker.
	if _, exists := tracker.m[traceId][uid]; !exists {
		// set the list of in flight uids for the new uid.
		tracker.m[traceId][uid] = list
	}
}

func (tracker *InFlightTracker) GetTrackedAndClear(traceId string, uid *faultload.FaultUid) []*faultload.FaultUid {
	tracker.Lock()
	defer tracker.Unlock()

	if _, exists := tracker.m[traceId]; !exists {
		return []*faultload.FaultUid{}
	}

	if list, exists := tracker.m[traceId][uid]; exists {
		delete(tracker.m[traceId], uid)
		return list
	}

	return []*faultload.FaultUid{}
}

func (tracker *InFlightTracker) ClearTracked(traceId string) {
	tracker.Lock()
	defer tracker.Unlock()

	delete(tracker.m, traceId)
}

func TrackFault(traceId string, uid *faultload.FaultUid) {
	inFlightTracker.Track(traceId, uid)
}

func GetTrackedAndClear(traceId string, uid *faultload.FaultUid) []*faultload.FaultUid {
	return inFlightTracker.GetTrackedAndClear(traceId, uid)
}

func ClearTracked(traceId string) {
	inFlightTracker.ClearTracked(traceId)
}
