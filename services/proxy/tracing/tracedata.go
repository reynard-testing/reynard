package tracing

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"log"
	"strconv"
	"strings"

	"dflipse.nl/ds-fit/shared/faultload"
)

type TraceStateData map[string]string

type TraceParentData struct {
	Version    string
	TraceID    faultload.TraceID
	ParentID   faultload.SpanID
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

func (t TraceStateData) Delete(key string) {
	delete(t, key)
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
func isValid(s []byte) bool {
	// check if not all zeros
	for _, v := range s {
		if v != 0 {
			return true
		}
	}
	return false
}

func NewSpanID() faultload.SpanID {
	sid := make([]byte, 8)
	for {
		if _, err := rand.Read(sid); err != nil {
			// Handle err
		}
		if isValid(sid) {
			break
		}
	}

	return faultload.SpanID(hex.EncodeToString(sid[:]))
}

func ParseTraceParent(traceparent string) *TraceParentData {
	if traceparent == "" {
		return nil
	}

	parts := strings.Split(traceparent, "-")
	if len(parts) != 4 {
		log.Println("Invalid traceparent format")
		return nil
	}

	return &TraceParentData{
		Version:    parts[0],
		TraceID:    faultload.TraceID(parts[1]),
		ParentID:   faultload.SpanID(parts[2]),
		TraceFlags: parts[3],
	}
}

func (parent *TraceParentData) GenerateNew() *TraceParentData {

	newSpan := NewSpanID()

	return &TraceParentData{
		Version:    parent.Version,
		TraceID:    parent.TraceID,
		ParentID:   newSpan,
		TraceFlags: parent.TraceFlags,
	}
}
