package store

import (
	"sync"

	"go.reynard.dev/instrumentation/shared/faultload"
)

type TraceInvocationCounter struct {
	sync.RWMutex
	m map[faultload.TraceID]map[string]int
}

func NewTraceInvocationCounter() *TraceInvocationCounter {
	return &TraceInvocationCounter{
		m: make(map[faultload.TraceID]map[string]int),
	}
}

var InvocationCounter = NewTraceInvocationCounter()

func getKey(stack faultload.FaultUid, partial faultload.PartialInjectionPoint, ips *faultload.InjectionPointCallStack) string {
	ipsString := ""
	if ips != nil {
		ipsString = ips.String()
	}
	key := stack.String() + ">" + partial.String() + ipsString
	return key
}

func (t *TraceInvocationCounter) getCountByKey(trace faultload.TraceID, key string) int {
	t.Lock()
	defer t.Unlock()

	if _, exists := t.m[trace]; !exists {
		t.m[trace] = make(map[string]int)
	}

	currentIndex, exists := t.m[trace][key]

	if !exists {
		currentIndex = 0
	} else {
		currentIndex++
	}

	t.m[trace][key] = currentIndex
	return currentIndex
}

func (t *TraceInvocationCounter) Clear(trace faultload.TraceID) {
	t.Lock()
	defer t.Unlock()

	delete(t.m, trace)
}

func (t *TraceInvocationCounter) GetCount(trace faultload.TraceID, stack faultload.FaultUid, partial faultload.PartialInjectionPoint, ips *faultload.InjectionPointCallStack) int {
	return t.getCountByKey(trace, getKey(stack, partial, ips))
}
