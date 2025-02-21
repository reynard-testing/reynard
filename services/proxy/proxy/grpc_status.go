package proxy

func toGrpcError(statusCode int) int {
	// 429 Too Many Requests
	// 502 Bad Gateway
	// 503 Service Unavailable
	// 504 Gateway Timeout
	if statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504 {
		return 14
	}

	// 400 Bad Request	INTERNAL
	// 401 Unauthorized	UNAUTHENTICATED
	// 403 Forbidden	PERMISSION_DENIED
	// 404 Not Found	UNIMPLEMENTED
	if statusCode == 408 {
		return 4
	}

	return 2
}

func toHttpError(statusCode int) int {
	if statusCode == 14 {
		return 503
	}

	if statusCode == 4 {
		return 408
	}

	return 2
}
