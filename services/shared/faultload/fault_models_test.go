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

	f1 := FaultUid{Stack: []InjectionPoint{{}}}
	f2 := FaultUid{Stack: []InjectionPoint{{
		Destination: "x",
	}}}
	f3 := FaultUid{Stack: []InjectionPoint{{
		Destination: "*",
	}}}

	f4 := FaultUid{Stack: []InjectionPoint{{
		CallStack: nil,
	}}}

	f5 := FaultUid{Stack: []InjectionPoint{{
		CallStack: &InjectionPointCallStack{},
	}}}

	f6 := FaultUid{Stack: []InjectionPoint{{
		CallStack: &InjectionPointCallStack{
			"1": 1,
		},
	}}}

	tests := []testCase{
		{
			name:  "empty stacks match",
			f1:    f1,
			f2:    f1,
			match: true,
		},
		{
			name:  "different destination",
			f1:    f1,
			f2:    f2,
			match: false,
		},
		{
			name:  "wildcard",
			f1:    f2,
			f2:    f3,
			match: true,
		},
		{
			name:  "wildcard stack <> itself",
			f1:    f4,
			f2:    f4,
			match: true,
		},
		{
			name:  "wildcard stack <> empty stack",
			f1:    f4,
			f2:    f5,
			match: true,
		},
		{
			name:  "empty <> wildcard stack",
			f1:    f5,
			f2:    f4,
			match: true,
		},
		{
			name:  "wildcard stack <> some value",
			f1:    f4,
			f2:    f6,
			match: true,
		},
		{
			name:  "empyt call stack <> some value",
			f1:    f5,
			f2:    f6,
			match: false,
		},
		// Add more cases as needed, e.g.:
		// {
		// 	name: "different stacks don't match",
		// 	f1: FaultUid{Stack: []InjectionPoint{{ID: 1}}},
		// 	f2: FaultUid{Stack: []InjectionPoint{{ID: 2}}},
		// 	match: false,
		// },
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := tc.f1.Matches(tc.f2); got != tc.match {
				t.Errorf("Expected match=%v, got %v", tc.match, got)
			}
		})
	}
}
