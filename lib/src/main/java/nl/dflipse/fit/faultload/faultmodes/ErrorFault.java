package nl.dflipse.fit.faultload.faultmodes;

import java.util.List;

public class ErrorFault {
  public static String FAULT_TYPE = "HTTP_ERROR";

  public static FaultMode fromError(HttpError error) {
    String errorCode = Integer.toString(error.getErrorCode());
    return new FaultMode(FAULT_TYPE, List.of(errorCode));
  }

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
}
