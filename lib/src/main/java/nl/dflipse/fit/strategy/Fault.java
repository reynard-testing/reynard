package nl.dflipse.fit.strategy;

import nl.dflipse.fit.strategy.faultmodes.FaultMode;

public class Fault {
  public FaultMode faultMode;
  public String spanUid;

  public Fault(FaultMode faultMode, String spanUid) {
    this.faultMode = faultMode;
    this.spanUid = spanUid;
  }
}
