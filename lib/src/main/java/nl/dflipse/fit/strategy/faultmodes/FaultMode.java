package nl.dflipse.fit.strategy.faultmodes;

import java.util.List;

public interface FaultMode {
  String getType();
  List<String> getArgs();
}
