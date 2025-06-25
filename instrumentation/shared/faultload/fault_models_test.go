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

	fEmpty := FaultUid{Stack: []InjectionPoint{{}}}
	fDest := FaultUid{Stack: []InjectionPoint{{
		Destination: nil,
	}}}
	fDestWildcard := FaultUid{Stack: []InjectionPoint{{
		Destination: nil,
	}}}

	fCount := FaultUid{Stack: []InjectionPoint{{
		Count: 0,
	}}}

	fCountWildcard := FaultUid{Stack: []InjectionPoint{{
		Count: -1,
	}}}

	fPred := FaultUid{Stack: []InjectionPoint{{
		Predecessors: &InjectionPointPredecessors{},
	}}}

	fPredWildcard := FaultUid{Stack: []InjectionPoint{{
		Predecessors: nil,
	}}}

	fPredStackDiff := FaultUid{Stack: []InjectionPoint{{
		Predecessors: &InjectionPointPredecessors{
			"1": 1,
		},
	}}}

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
			f2:    fDest,
			match: false,
		},
		{
			name:  "destination wildcard",
			f1:    fDest,
			f2:    fDestWildcard,
			match: true,
		},
		{
			name:  "different count",
			f1:    fEmpty,
			f2:    fCount,
			match: false,
		},
		{
			name:  "count wildcard",
			f1:    fCount,
			f2:    fCountWildcard,
			match: true,
		},
		{
			name:  "count wildcard",
			f1:    fCount,
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
			f2:    fPred,
			match: true,
		},
		{
			name:  "empty <> wildcard stack",
			f1:    fPred,
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
			f1:    fPred,
			f2:    fPredStackDiff,
			match: false,
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
