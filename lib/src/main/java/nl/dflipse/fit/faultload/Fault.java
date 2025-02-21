package nl.dflipse.fit.faultload;

import nl.dflipse.fit.faultload.faultmodes.FaultMode;

public class Fault {
  public FaultMode faultMode;
  public String spanUid;

  public Fault(FaultMode faultMode, String spanUid) {
    this.faultMode = faultMode;
    this.spanUid = spanUid;
  }
}
