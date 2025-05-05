package tracing

import (
	"sync"

	"dflipse.nl/ds-fit/shared/faultload"
)

type TraceInvocationCounter struct {
	sync.RWMutex
	m map[faultload.TraceID]map[string]int
}

var traceInvocationCounter = TraceInvocationCounter{m: make(map[faultload.TraceID]map[string]int)}

func getKey(stack faultload.FaultUid, partial faultload.PartialInjectionPoint, ips faultload.InjectionPointCallStack) string {
	key := stack.String() + ">" + partial.String() + ips.String()
	return key
}

func (t *TraceInvocationCounter) GetCount(trace faultload.TraceID, key string) int {
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

func GetCountForTrace(trace faultload.TraceID, stack faultload.FaultUid, partial faultload.PartialInjectionPoint, ips faultload.InjectionPointCallStack) int {
	return traceInvocationCounter.GetCount(trace, getKey(stack, partial, ips))
}

func ClearTraceCount(trace faultload.TraceID) {
	traceInvocationCounter.Clear(trace)
}
