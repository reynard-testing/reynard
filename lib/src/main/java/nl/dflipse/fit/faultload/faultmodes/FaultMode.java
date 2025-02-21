package nl.dflipse.fit.faultload.faultmodes;

import java.util.List;

public interface FaultMode {
  String getType();
  List<String> getArgs();
}
