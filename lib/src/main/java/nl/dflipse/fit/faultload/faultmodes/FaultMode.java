package nl.dflipse.fit.faultload.faultmodes;

import java.util.List;

public record FaultMode(String type, List<String> args) {
  public String getType() {
    return type;
  }

  public List<String> getArgs() {
    return args;
  }

  public String toString() {
    return type + "(" + String.join(", ", args) + ")";
  }
}