package nl.dflipse.fit.faultload.faultmodes;

import java.util.List;

public class DelayFault {
  public static String FAULT_TYPE = "DELAY";

  public static FailureMode fromDelayMs(int delayMs) {
    String intDelayMs = Integer.toString(delayMs);
    return new FailureMode(FAULT_TYPE, List.of(intDelayMs));
  }
}
