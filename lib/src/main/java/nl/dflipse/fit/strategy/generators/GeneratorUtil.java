package nl.dflipse.fit.strategy.generators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;

public class GeneratorUtil {

  public static Set<Fault> asFaults(List<FaultUid> ids, FaultMode mode) {
    Set<Fault> faults = new HashSet<>();

    for (var id : ids) {
      faults.add(new Fault(id, mode));
    }

    return faults;
  }

  public static List<Faultload> allFaults(List<FaultMode> modes, List<List<FaultUid>> faultCombinations) {
    List<Faultload> faultLoads = new ArrayList<>();

    for (var combination : faultCombinations) {
      if (combination.isEmpty()) {
        continue;
      }

      for (var mode : modes) {
        faultLoads.add(new Faultload(asFaults(combination, mode)));
      }
    }

    return faultLoads;
  }
}
