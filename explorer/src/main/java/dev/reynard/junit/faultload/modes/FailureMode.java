package dev.reynard.junit.faultload.modes;

import java.util.List;

public record FailureMode(String type, List<String> args) {
  public String getType() {
    return type;
  }

  public List<String> getArgs() {
    return args;
  }

  @Override
  public String toString() {
    return type + "(" + String.join(", ", args) + ")";
  }
}
