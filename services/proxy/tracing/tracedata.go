package tracing

import (
	"fmt"
	"strconv"
	"strings"
)

type TraceStateData map[string]string

type TraceParentData struct {
	Version    string
	TraceID    string
	ParentID   string
	TraceFlags string
}

func (t TraceStateData) String() string {
	if t == nil {
		return ""
	}

	var pairs []string
	for k, v := range t {
		pairs = append(pairs, fmt.Sprintf("%s=%s", k, v))
	}

	return strings.Join(pairs, ",")
}

func (t TraceStateData) Set(key, value string) {
	t[key] = value
}

func (t TraceStateData) HasKeys() bool {
	return len(t) > 0
}

func (t TraceStateData) SetInt(key string, value int) {
	t.Set(key, strconv.Itoa(value))
}

func (t TraceStateData) Get(key string) string {
	return t.GetWithDefault(key, "")
}

func (t *TraceStateData) GetWithDefault(key string, def string) string {
	if t == nil {
		return def
	}

	if val, ok := (*t)[key]; ok {
		return val
	}

	return def
}

func (t *TraceStateData) GetIntWithDefault(key string, def int) int {
	if t == nil {
		return def
	}

	if val, ok := (*t)[key]; ok {
		i, err := strconv.Atoi(val)
		if err != nil {
			return def
		}

		return i
	}

	return def
}

func ParseTraceState(tracestate string) *TraceStateData {
	entries := make(TraceStateData)

	if tracestate == "" {
		return &entries
	}

	pairs := strings.Split(tracestate, ",")
	for _, pair := range pairs {
		kv := strings.SplitN(pair, "=", 2)
		if len(kv) == 2 {
			entries[kv[0]] = kv[1]
		}
	}

	return &entries
}

func (t *TraceParentData) String() string {
	return fmt.Sprintf("%s-%s-%s-%s", t.Version, t.TraceID, t.ParentID, t.TraceFlags)
}

func ParseTraceParent(traceparent string) *TraceParentData {
	if traceparent == "" {
		return nil
	}

	parts := strings.Split(traceparent, "-")
	if len(parts) != 4 {
		fmt.Println("Invalid traceparent format")
		return nil
	}

	return &TraceParentData{
		Version:    parts[0],
		TraceID:    parts[1],
		ParentID:   parts[2],
		TraceFlags: parts[3],
	}
}
