package endpoints

import (
	"reflect"
	"testing"
)

func TestParseProxyList(t *testing.T) {
	cases := map[string][]string{
		"":                {},
		"a":               {"a"},
		"a,b":             {"a", "b"},
		"a,,b":            {"a", "b"},
		"localhost:5001,": {"localhost:5001"},
	}

	for input, want := range cases {
		got := parseProxyList(input)
		if !reflect.DeepEqual(got, want) {
			t.Errorf("parseProxyList(%q) = %v, want %v", input, got, want)
		}
	}
}
