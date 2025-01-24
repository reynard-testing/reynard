package nl.dflipse.fit.strategy.faultmodes;

import java.util.List;

public class ErrorFault implements FaultMode {
  public enum HttpError {
    SERVICE_UNAVAILABLE(503),
    GATEWAY_TIMEOUT(504),
    INTERNAL_SERVER_ERROR(500),
    BAD_GATEWAY(502),
    NOT_IMPLEMENTED(501),
    HTTP_VERSION_NOT_SUPPORTED(505),
    NETWORK_AUTHENTICATION_REQUIRED(511);

    private final int errorCode;

    HttpError(int errorCode) {
      this.errorCode = errorCode;
    }

    public int getErrorCode() {
      return errorCode;
    }
  }

  private final HttpError error;

  public ErrorFault(HttpError error) {
    this.error = error;
  }

  @Override
  public String getType() {
    return "HTTP_ERROR";
  }

  @Override
  public List<String> getArgs() {
    int errorCode = error.getErrorCode();
    return List.of(
      Integer.toString(errorCode)
    );
  }
}
