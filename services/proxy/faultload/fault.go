package faultload

import "fmt"

type FaultUid struct {
	Origin      string `json:"origin"`
	Destination string `json:"destination"`
	Signature   string `json:"signature"`
	Count       int    `json:"count"`
}

func (f1 FaultUid) Matches(f2 FaultUid) bool {
	return f1.Origin == f2.Origin && f1.Destination == f2.Destination && f1.Signature == f2.Signature && f1.Count == f2.Count
}

func (f FaultUid) String() string {
	return fmt.Sprintf("%s>%s:%s|%d", f.Origin, f.Destination, f.Signature, f.Count)
}

type Fault struct {
	Uid  FaultUid  `json:"uid"`
	Mode FaultMode `json:"mode"`
}

type FaultMode struct {
	Type string   `json:"type"`
	Args []string `json:"args"`
}
