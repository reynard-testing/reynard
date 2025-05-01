package io.github.delanoflipse.fit.suite.faultload.modes;

import java.util.List;

public class OmissionFault {
  public static String FAULT_TYPE = "OMISSION_ERROR";

  public static FailureMode fromError(HttpError error) {
    String errorCode = Integer.toString(error.getErrorCode());
    return new FailureMode(FAULT_TYPE, List.of(errorCode));
  }
}
