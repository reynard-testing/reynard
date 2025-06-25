package faultload

import (
	"fmt"
	"sort"
	"strings"
)

type PartialInjectionPoint struct {
	Destination *string `json:"destination"`
	Signature   *string `json:"signature"`
	Payload     *string `json:"payload"`
}

func (f PartialInjectionPoint) String() string {
	payloadStr := ""
	if f.Payload != nil && *f.Payload != "" {
		payloadStr = fmt.Sprintf("(%s)", *f.Payload)
	}

	return fmt.Sprintf("%s:%s%s", safeString(f.Destination), safeString(f.Signature), payloadStr)
}

type InjectionPointPredecessors map[string]int

type InjectionPoint struct {
	Destination  *string                     `json:"destination"`
	Signature    *string                     `json:"signature"`
	Payload      *string                     `json:"payload"`
	Predecessors *InjectionPointPredecessors `json:"predecessors"`
	Count        int                         `json:"count"`
}

func (p InjectionPoint) WithDestination(v *string) InjectionPoint {
	return InjectionPoint{
		Destination:  v,
		Signature:    p.Signature,
		Payload:      p.Payload,
		Predecessors: p.Predecessors,
		Count:        p.Count,
	}
}

func (p InjectionPoint) WithSignature(v *string) InjectionPoint {
	return InjectionPoint{
		Destination:  p.Destination,
		Signature:    v,
		Payload:      p.Payload,
		Predecessors: p.Predecessors,
		Count:        p.Count,
	}
}

func (p InjectionPoint) WithPayload(v *string) InjectionPoint {
	return InjectionPoint{
		Destination:  p.Destination,
		Signature:    p.Signature,
		Payload:      v,
		Predecessors: p.Predecessors,
		Count:        p.Count,
	}
}

func (p InjectionPoint) WithPredecessors(v *InjectionPointPredecessors) InjectionPoint {
	return InjectionPoint{
		Destination:  p.Destination,
		Signature:    p.Signature,
		Payload:      p.Payload,
		Predecessors: v,
		Count:        p.Count,
	}
}

func (p InjectionPoint) WithCount(v int) InjectionPoint {
	return InjectionPoint{
		Destination:  p.Destination,
		Signature:    p.Signature,
		Payload:      p.Payload,
		Predecessors: p.Predecessors,
		Count:        v,
	}
}

func (p InjectionPoint) AsPartial() PartialInjectionPoint {
	return PartialInjectionPoint{
		Destination: p.Destination,
		Signature:   p.Signature,
		Payload:     p.Payload,
	}
}

type FaultUid struct {
	Stack []*InjectionPoint `json:"stack"`
}

func (f FaultUid) Parent() FaultUid {
	if len(f.Stack) == 0 {
		return FaultUid{}
	}

	return FaultUid{
		Stack: f.Stack[:len(f.Stack)-1],
	}
}

func (f FaultUid) IsAny() bool {
	return len(f.Stack) == 1 && f.Stack[0] == nil
}

func (f FaultUid) IsAnyOrigin() bool {
	return len(f.Stack) >= 1 && f.Stack[0] == nil
}

func (f FaultUid) Point() *InjectionPoint {
	if len(f.Stack) == 0 {
		return nil
	}

	return f.Stack[len(f.Stack)-1]
}

func safeString(s *string) string {
	if s == nil {
		return ""
	}

	return *s
}

func stringMatches(v1, v2 *string) bool {
	return v1 == nil || v2 == nil || *v1 == *v2
}

func intMatches(v1, v2 int) bool {
	return v1 == v2 || v1 < 0 || v2 < 0
}

func (f1 FaultUid) Matches(f2 FaultUid) bool {
	if f1.Stack == nil || f2.Stack == nil {
		return true
	}

	if f1.IsAny() || f2.IsAny() {
		return true
	}

	if f1.IsAnyOrigin() || f2.IsAnyOrigin() {
		return (*f1.Point()).Matches(*f2.Point())
	}

	if len(f1.Stack) != len(f2.Stack) {
		return false
	}

	for i := range f1.Stack {
		if f1.Stack[i] == nil || f2.Stack[i] == nil {
			continue
		}

		if !f1.Stack[i].Matches(*f2.Stack[i]) {
			return false
		}
	}

	return true
}

func (cs1 InjectionPointPredecessors) Matches(cs2 *InjectionPointPredecessors) bool {
	if cs2 == nil {
		return true
	}

	if len(cs1) != len(*cs2) {
		return false
	}

	for k, v := range cs1 {
		if v2, ok := (*cs2)[k]; !ok || !intMatches(v, v2) {
			return false
		}
	}

	return true
}

func (f1 InjectionPoint) Matches(f2 InjectionPoint) bool {
	return stringMatches(f1.Destination, f2.Destination) &&
		stringMatches(f1.Signature, f2.Signature) &&
		stringMatches(f1.Payload, f2.Payload) &&
		(f1.Predecessors == nil || f1.Predecessors.Matches(f2.Predecessors)) &&
		intMatches(f1.Count, f2.Count)
}

func (fid FaultUid) String() string {
	ip_strings := make([]string, len(fid.Stack))
	for i, ip := range fid.Stack {
		ip_strings[i] = ip.String()
	}
	return strings.Join(ip_strings, ">")
}

func (cs InjectionPointPredecessors) String() string {
	if len(cs) == 0 {
		return ""
	}

	csStrings := make([]string, len(cs))

	// Sort the keys to ensure consistent ordering
	keys := make([]string, 0, len(cs))
	for p := range cs {
		keys = append(keys, p)
	}
	sort.Strings(keys)

	// Create the string representation
	for _, p := range keys {
		csStrings = append(csStrings, fmt.Sprintf("%s:%d", p, cs[p]))
	}
	return fmt.Sprintf("{%s}", strings.Join(csStrings, ","))
}

func (ips InjectionPointPredecessors) Del(point PartialInjectionPoint) {
	if len(ips) == 0 {
		return
	}

	key := point.String()
	delete(ips, key)
}

func (f InjectionPoint) String() string {
	payloadStr := ""
	if f.Payload != nil && *f.Payload != "" {
		payloadStr = fmt.Sprintf("(%s)", *f.Payload)
	}

	countStr := ""
	if f.Count < 0 {
		countStr = "#∞"
	} else {
		countStr = fmt.Sprintf("#%d", f.Count)
	}

	csStr := ""
	if f.Predecessors != nil {
		csStr = f.Predecessors.String()
	}

	return fmt.Sprintf("%s:%s%s%s%s", safeString(f.Destination), safeString(f.Signature), payloadStr, csStr, countStr)
}

type Fault struct {
	Uid  FaultUid  `json:"uid"`
	Mode FaultMode `json:"mode"`
}

type FaultMode struct {
	Type string   `json:"type"`
	Args []string `json:"args"`
}

func BuildFaultUid(parent FaultUid, partial PartialInjectionPoint, ips *InjectionPointPredecessors, count int) FaultUid {
	fid := make([]*InjectionPoint, len(parent.Stack)+1)
	// Copy the existing stack
	copy(fid, parent.Stack)

	// Add the new injection point
	fid[len(parent.Stack)] = &InjectionPoint{
		Destination:  partial.Destination,
		Signature:    partial.Signature,
		Payload:      partial.Payload,
		Predecessors: ips,
		Count:        count,
	}

	return FaultUid{
		Stack: fid,
	}
}
