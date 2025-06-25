package faultload

import (
	"testing"
)

func TestMatch(t *testing.T) {
	type testCase struct {
		name  string
		f1    FaultUid
		f2    FaultUid
		match bool
	}

	fEmpty := FaultUid{Stack: []*InjectionPoint{}}
	fAny := FaultUid{Stack: []*InjectionPoint{nil}}
	emp := ""
	iproot := InjectionPoint{
		Destination:  &emp,
		Signature:    &emp,
		Payload:      &emp,
		Predecessors: &InjectionPointPredecessors{},
		Count:        0,
	}

	destA := "A"
	ipA := iproot.WithDestination(&destA)

	destB := "B"
	ipB := iproot.WithDestination(&destB)

	destC := "C"
	ipC := iproot.WithDestination(&destC)

	ipWithoutDest := ipA.WithDestination(nil)
	ipWithoutCount := ipA.WithCount(-1)
	ipWithoutPred := ipA.WithPredecessors(nil)

	ipWithCount1 := ipA.WithCount(1)
	ipWithOhterPred := ipA.WithPredecessors(&InjectionPointPredecessors{
		"1": 1,
	})

	fA := FaultUid{Stack: []*InjectionPoint{
		&ipA,
	}}

	fAB := FaultUid{Stack: []*InjectionPoint{
		&ipA,
		&ipB,
	}}

	fAC := FaultUid{Stack: []*InjectionPoint{
		&ipA,
		&ipC,
	}}

	fBC := FaultUid{Stack: []*InjectionPoint{
		&ipB,
		&ipC,
	}}

	fxC := FaultUid{Stack: []*InjectionPoint{
		nil,
		&ipC,
	}}

	fDestWildcard := FaultUid{Stack: []*InjectionPoint{
		&ipWithoutDest,
	}}

	fCountWildcard := FaultUid{Stack: []*InjectionPoint{
		&ipWithoutCount,
	}}

	fWithCount1 := FaultUid{Stack: []*InjectionPoint{
		&ipWithCount1,
	}}

	fPredWildcard := FaultUid{Stack: []*InjectionPoint{
		&ipWithoutPred,
	}}

	fPredStackDiff := FaultUid{Stack: []*InjectionPoint{
		&ipWithOhterPred,
	}}

	tests := []testCase{
		{
			name:  "empty stacks match",
			f1:    fEmpty,
			f2:    fEmpty,
			match: true,
		},
		{
			name:  "different destination",
			f1:    fEmpty,
			f2:    fA,
			match: false,
		},
		{
			name:  "destination wildcard",
			f1:    fA,
			f2:    fDestWildcard,
			match: true,
		},
		{
			name:  "different count",
			f1:    fA,
			f2:    fWithCount1,
			match: false,
		},
		{
			name:  "count wildcard",
			f1:    fA,
			f2:    fCountWildcard,
			match: true,
		},
		{
			name:  "wildcard stack <> itself",
			f1:    fPredWildcard,
			f2:    fPredWildcard,
			match: true,
		},
		{
			name:  "wildcard stack <> empty stack",
			f1:    fPredWildcard,
			f2:    fA,
			match: true,
		},
		{
			name:  "empty <> wildcard stack",
			f1:    fA,
			f2:    fPredWildcard,
			match: true,
		},
		{
			name:  "wildcard stack <> some value",
			f1:    fPredWildcard,
			f2:    fPredStackDiff,
			match: true,
		},
		{
			name:  "empyt predecessors <> some value",
			f1:    fA,
			f2:    fPredStackDiff,
			match: false,
		},
		{
			name:  "different destination",
			f1:    fAB,
			f2:    fAC,
			match: false,
		},
		{
			name:  "different cause",
			f1:    fBC,
			f2:    fAC,
			match: false,
		},
		{
			name:  "different cause (with origin wildcard)",
			f1:    fAB,
			f2:    fxC,
			match: false,
		},
		{
			name:  "match any",
			f1:    fAny,
			f2:    fAC,
			match: true,
		},
		{
			name:  "match origin",
			f1:    fxC,
			f2:    fAC,
			match: true,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := tc.f1.Matches(tc.f2); got != tc.match {
				t.Errorf("Expected match=%v, got %v", tc.match, got)
			}
		})
	}
}
