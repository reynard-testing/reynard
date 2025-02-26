package tracing

import "sync"

type TraceInvocationCounter struct {
	sync.RWMutex
	m map[string]int
}

var traceInvocationCounter = TraceInvocationCounter{m: make(map[string]int)}

func (t *TraceInvocationCounter) GetCount(key string) int {
	t.Lock()
	defer t.Unlock()

	currentIndex, exists := t.m[key]

	if !exists {
		currentIndex = 0
	} else {
		currentIndex++
	}

	t.m[key] = currentIndex
	return currentIndex
}
