package nl.dflipse.fit.strategy;

import nl.dflipse.fit.strategy.faultmodes.FaultMode;

public class Fault {
  public FaultMode faultMode;
  public String spanId;

  public Fault(FaultMode faultMode, String spanId) {
    this.faultMode = faultMode;
    this.spanId = spanId;
  }
}
