package faultload

import "fmt"

type FaultUid struct {
	Origin      string `json:"origin"`
	Destination string `json:"destination"`
	Signature   string `json:"signature"`
	Payload     string `json:"payload"`
	Count       int    `json:"count"`
}

func stringMatches(v1, v2 string) bool {
	return v1 == v2 || v1 == "*" || v2 == "*"
}

func intMatches(v1, v2 int) bool {
	return v1 == v2 || v1 < 0 || v2 < 0
}

func (f1 FaultUid) Matches(f2 FaultUid) bool {
	return stringMatches(f1.Origin, f2.Origin) &&
		stringMatches(f1.Destination, f2.Destination) &&
		stringMatches(f1.Signature, f2.Signature) &&
		stringMatches(f1.Payload, f2.Payload) &&
		intMatches(f1.Count, f2.Count)
}

func (f FaultUid) String() string {
	return fmt.Sprintf("%s>%s:%s(%s)#%d", f.Origin, f.Destination, f.Signature, f.Payload, f.Count)
}

type Fault struct {
	Uid  FaultUid  `json:"uid"`
	Mode FaultMode `json:"mode"`
}

type FaultMode struct {
	Type string   `json:"type"`
	Args []string `json:"args"`
}
