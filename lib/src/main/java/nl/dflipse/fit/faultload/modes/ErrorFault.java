package io.github.delanoflipse.fit.faultload.modes;

import java.util.List;

public class ErrorFault {
  public static String FAULT_TYPE = "HTTP_ERROR";

  public static FailureMode fromError(HttpError error) {
    String errorCode = Integer.toString(error.getErrorCode());
    return new FailureMode(FAULT_TYPE, List.of(errorCode));
  }

}
