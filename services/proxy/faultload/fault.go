package faultload

import (
	"fmt"
	"sort"
	"strings"
)

type PartialInjectionPoint struct {
	Destination string `json:"destination"`
	Signature   string `json:"signature"`
	Payload     string `json:"payload"`
}

type InjectionPointClock map[string]int

type InjectionPoint struct {
	Destination string              `json:"destination"`
	Signature   string              `json:"signature"`
	Payload     string              `json:"payload"`
	VectorClock InjectionPointClock `json:"vector_clock"`
	Count       int                 `json:"count"`
}
type FaultUid struct {
	Stack []InjectionPoint `json:"stack"`
}

func stringMatches(v1, v2 string) bool {
	return v1 == v2 || v1 == "*" || v2 == "*"
}

func intMatches(v1, v2 int) bool {
	return v1 == v2 || v1 < 0 || v2 < 0
}

func (f1 FaultUid) Matches(f2 FaultUid) bool {
	if len(f1.Stack) != len(f2.Stack) {
		return false
	}

	for i := range f1.Stack {
		if !f1.Stack[i].Matches(f2.Stack[i]) {
			return false
		}
	}

	return true
}

func (f1 InjectionPoint) Matches(f2 InjectionPoint) bool {
	return stringMatches(f1.Destination, f2.Destination) &&
		stringMatches(f1.Signature, f2.Signature) &&
		stringMatches(f1.Payload, f2.Payload) &&
		intMatches(f1.Count, f2.Count)
}

func (fid FaultUid) String() string {
	ip_strings := make([]string, len(fid.Stack))
	for i, ip := range fid.Stack {
		ip_strings[i] = ip.String()
	}
	return strings.Join(ip_strings, ">")
}

func (clock InjectionPointClock) String() string {
	if len(clock) == 0 {
		return ""
	}

	vcs := make([]string, len(clock))

	// Sort the keys to ensure consistent ordering
	keys := make([]string, 0, len(clock))
	for p := range clock {
		keys = append(keys, p)
	}
	sort.Strings(keys)

	// Create the string representation
	for _, p := range keys {
		vcs = append(vcs, fmt.Sprintf("%s:%d", p, clock[p]))
	}
	return fmt.Sprintf("{%s}", strings.Join(vcs, ","))
}

func (clock InjectionPointClock) Del(point PartialInjectionPoint) {
	if len(clock) == 0 {
		return
	}

	key := point.String()
	delete(clock, key)
}

func (f InjectionPoint) String() string {
	payloadStr := ""
	if f.Payload != "*" && f.Payload != "" {
		payloadStr = fmt.Sprintf("(%s)", f.Payload)
	}

	countStr := ""
	if f.Count < 0 {
		countStr = "#∞"
	} else {
		countStr = fmt.Sprintf("#%d", f.Count)
	}

	vcStr := f.VectorClock.String()

	return fmt.Sprintf("%s:%s%s%s%s", f.Destination, f.Signature, payloadStr, vcStr, countStr)
}

func (f PartialInjectionPoint) String() string {
	payloadStr := ""
	if f.Payload != "*" && f.Payload != "" {
		payloadStr = fmt.Sprintf("(%s)", f.Payload)
	}

	return fmt.Sprintf("%s:%s%s", f.Destination, f.Signature, payloadStr)
}

type Fault struct {
	Uid  FaultUid  `json:"uid"`
	Mode FaultMode `json:"mode"`
}

type FaultMode struct {
	Type string   `json:"type"`
	Args []string `json:"args"`
}

func BuildFaultUid(parent FaultUid, partial PartialInjectionPoint, vc InjectionPointClock, count int) FaultUid {
	fid := make([]InjectionPoint, len(parent.Stack)+1)
	// Copy the existing stack
	copy(fid, parent.Stack)

	// Add the new injection point
	fid[len(parent.Stack)] = InjectionPoint{
		Destination: partial.Destination,
		Signature:   partial.Signature,
		Payload:     partial.Payload,
		VectorClock: vc,
		Count:       count,
	}

	return FaultUid{
		Stack: fid,
	}
}
