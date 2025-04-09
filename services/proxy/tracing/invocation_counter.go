package tracing

import (
	"sync"

	"dflipse.nl/fit-proxy/faultload"
)

type TraceInvocationCounter struct {
	sync.RWMutex
	m map[string]map[string]int
}

var traceInvocationCounter = TraceInvocationCounter{m: make(map[string]map[string]int)}

func getKey(stack faultload.FaultUid, partial faultload.PartialInjectionPoint) string {
	return stack.String() + ">" + partial.String()
}

func (t *TraceInvocationCounter) GetCount(trace, key string) int {
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

func (t *TraceInvocationCounter) Clear(trace string) {
	t.Lock()
	defer t.Unlock()

	delete(t.m, trace)
}

func GetCountForTrace(trace string, stack faultload.FaultUid, partial faultload.PartialInjectionPoint) int {
	return traceInvocationCounter.GetCount(trace, getKey(stack, partial))
}

func ClearTraceCount(trace string) {
	traceInvocationCounter.Clear(trace)
}
