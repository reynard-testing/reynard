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
		Destination: "x",
	}}}
	fDestWildcard := FaultUid{Stack: []InjectionPoint{{
		Destination: "*",
	}}}

	fCount := FaultUid{Stack: []InjectionPoint{{
		Count: 0,
	}}}

	fCountWildcard := FaultUid{Stack: []InjectionPoint{{
		Count: -1,
	}}}

	fCallStack := FaultUid{Stack: []InjectionPoint{{
		CallStack: &InjectionPointCallStack{},
	}}}

	fCallStackWildcard := FaultUid{Stack: []InjectionPoint{{
		CallStack: nil,
	}}}

	fCallStackDifference := FaultUid{Stack: []InjectionPoint{{
		CallStack: &InjectionPointCallStack{
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
			f1:    fCallStackWildcard,
			f2:    fCallStackWildcard,
			match: true,
		},
		{
			name:  "wildcard stack <> empty stack",
			f1:    fCallStackWildcard,
			f2:    fCallStack,
			match: true,
		},
		{
			name:  "empty <> wildcard stack",
			f1:    fCallStack,
			f2:    fCallStackWildcard,
			match: true,
		},
		{
			name:  "wildcard stack <> some value",
			f1:    fCallStackWildcard,
			f2:    fCallStackDifference,
			match: true,
		},
		{
			name:  "empyt call stack <> some value",
			f1:    fCallStack,
			f2:    fCallStackDifference,
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
