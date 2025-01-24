package nl.dflipse.fit.strategy.faultmodes;

import java.util.List;

public class DelayFault implements FaultMode {
  private final int delayMs;

  public DelayFault(int delayMs) {
    this.delayMs = delayMs;
  }

  @Override
  public String getType() {
    return "DELAY";
  }

  @Override
  public List<String> getArgs() {
    return List.of(
        Integer.toString(delayMs));
  }
}
