package store

import (
	"sync"

	"dflipse.nl/ds-fit/shared/faultload"
)

type TraceUIDStore struct {
	traceIDs  map[faultload.TraceID]struct{}
	traceLock sync.RWMutex
}

func NewTraceUIDStore() *TraceUIDStore {
	return &TraceUIDStore{
		traceIDs: make(map[faultload.TraceID]struct{}),
	}
}

var TraceIds = NewTraceUIDStore()

func (s *TraceUIDStore) Register(traceID faultload.TraceID) {
	s.traceLock.Lock()
	defer s.traceLock.Unlock()
	s.traceIDs[traceID] = struct{}{}
}

func (s *TraceUIDStore) IsRegistered(traceID faultload.TraceID) bool {
	s.traceLock.RLock()
	defer s.traceLock.RUnlock()
	_, ok := s.traceIDs[traceID]
	return ok
}

func (s *TraceUIDStore) Unregister(traceID faultload.TraceID) {
	s.traceLock.Lock()
	defer s.traceLock.Unlock()
	delete(s.traceIDs, traceID)
}

func (s *TraceUIDStore) Clear() {
	s.traceLock.Lock()
	defer s.traceLock.Unlock()
	s.traceIDs = make(map[faultload.TraceID]struct{})
}
