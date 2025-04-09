package faultload

import (
	"fmt"
	"strings"
)

type PartialInjectionPoint struct {
	Destination string `json:"destination"`
	Signature   string `json:"signature"`
	Payload     string `json:"payload"`
}

type InjectionPoint struct {
	Destination string `json:"destination"`
	Signature   string `json:"signature"`
	Payload     string `json:"payload"`
	Count       int    `json:"count"`
}
type FaultUid []InjectionPoint

func stringMatches(v1, v2 string) bool {
	return v1 == v2 || v1 == "*" || v2 == "*"
}

func intMatches(v1, v2 int) bool {
	return v1 == v2 || v1 < 0 || v2 < 0
}

func (f1 FaultUid) Matches(f2 FaultUid) bool {
	if len(f1) != len(f2) {
		return false
	}

	for i := range f1 {
		if !f1[i].Matches(f2[i]) {
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
	ip_strings := make([]string, len(fid))
	for i, ip := range fid {
		ip_strings[i] = ip.String()
	}
	return strings.Join(ip_strings, ">")
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
	return fmt.Sprintf("%s:%s%s%s", f.Destination, f.Signature, payloadStr, countStr)
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

func BuildFaultUid(stack []InjectionPoint, partial PartialInjectionPoint, count int) FaultUid {
	fid := make([]InjectionPoint, len(stack)+1)
	// Copy the existing stack
	copy(fid, stack)
	// Add the new injection point
	fid[len(stack)] = InjectionPoint{
		Destination: partial.Destination,
		Signature:   partial.Signature,
		Payload:     partial.Payload,
		Count:       count,
	}

	return fid
}
