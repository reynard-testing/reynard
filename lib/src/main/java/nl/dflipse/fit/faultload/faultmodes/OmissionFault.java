package nl.dflipse.fit.faultload.faultmodes;

import java.util.List;

public class OmissionFault {
  public static String FAULT_TYPE = "OMISSION_ERROR";

  public static FaultMode fromError(HttpError error) {
    String errorCode = Integer.toString(error.getErrorCode());
    return new FaultMode(FAULT_TYPE, List.of(errorCode));
  }
}
