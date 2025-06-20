package dev.reynard.junit.faultload.modes;

public enum HttpError {
    INTERNAL_SERVER_ERROR(500),
    NOT_IMPLEMENTED(501),
    BAD_GATEWAY(502),
    SERVICE_UNAVAILABLE(503),
    GATEWAY_TIMEOUT(504),
    HTTP_VERSION_NOT_SUPPORTED(505),
    NETWORK_AUTHENTICATION_REQUIRED(511),

    REQUEST_TIMEOUT(408);

    private final int errorCode;

    HttpError(int errorCode) {
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}