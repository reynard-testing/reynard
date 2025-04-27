package proxy

// --- gRPC status codes ---
// UNKNOWN -> 2
// DEADLINE_EXCEEDED -> 4
// RESOURCE_EXHAUSTED -> 8
// ABORTED -> 10
// INTERNAL -> 13
// UNAVAILABLE -> 14
// DATA_LOSS -> 15

// --- HTTP status codes ---
// 429 Too Many Requests
// 500 Internal Server Error
// 502 Bad Gateway
// 503 Service Unavailable

// 400 Bad Request	INTERNAL
// 401 Unauthorized	UNAUTHENTICATED
// 403 Forbidden	PERMISSION_DENIED
// 404 Not Found	UNIMPLEMENTED
// 408 Reqeust Timeout
// 504 Gateway Timeout

func toGrpcError(statusCode int) int {

	if statusCode == 429 || statusCode == 503 {
		return 14
	}

	if statusCode == 502 {
		return 13
	}

	if statusCode == 408 || statusCode == 504 {
		return 4
	}

	return 2
}

func toHttpError(statusCode int) int {
	if statusCode == 14 {
		return 503
	}

	if statusCode == 13 {
		return 502
	}

	if statusCode == 4 {
		return 504
	}

	// 2 & others
	return 500
}
