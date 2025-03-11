package nl.dflipse.fit.faultload.faultmodes;

import java.util.List;

public class ErrorFault {
  public static String FAULT_TYPE = "HTTP_ERROR";

  public static FaultMode fromError(HttpError error) {
    String errorCode = Integer.toString(error.getErrorCode());
    return new FaultMode(FAULT_TYPE, List.of(errorCode));
  }

}
