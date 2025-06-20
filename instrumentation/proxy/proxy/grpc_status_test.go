package proxy

import (
	"fmt"
	"testing"
)

func TestReversible(t *testing.T) {
	// Test cases
	testCases := []struct {
		input int
	}{
		{500},
		{502},
		{503},
		{504},
	}

	for _, tc := range testCases {
		testName := fmt.Sprintf("Test Reversible %d", tc.input)

		t.Run(testName, func(t *testing.T) {
			grpcError := toGrpcError(tc.input)
			reversed := toHttpError(grpcError)
			if reversed != tc.input {
				t.Errorf("Expected %d, got %d", tc.input, reversed)
			}
		})
	}
}
