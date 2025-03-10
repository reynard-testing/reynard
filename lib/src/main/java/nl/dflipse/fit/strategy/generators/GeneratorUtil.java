package nl.dflipse.fit.strategy.generators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.util.Combinatorics;
import nl.dflipse.fit.strategy.util.Pair;

public class GeneratorUtil {

  public static Set<Fault> asFaults(List<FaultUid> ids, FaultMode mode) {
    Set<Fault> faults = new HashSet<>();

    for (var id : ids) {
      faults.add(new Fault(id, mode));
    }

    return faults;
  }

  public static <X, Y> List<Pair<X, Y>> cartesianProduct(List<X> xs, List<Y> ys) {
    List<Pair<X, Y>> pairs = new ArrayList<>();

    for (var x : xs) {
      for (var y : ys) {
        pairs.add(new Pair<>(x, y));
      }
    }

    return pairs;
  }

  public static List<Faultload> allCombinations(List<FaultMode> modes, List<FaultUid> points) {
    // Generate all combinations of faults
    var combinations = Combinatorics.cartesianCombinations(points, modes);

    return combinations.stream()
        // Convert list of pairs to set of faults
        .map(faults -> faults
            .stream()
            .map(pair -> new Fault(pair.first(), pair.second()))
            .collect(Collectors.toSet()))
        // Convert set of faults to faultload
        .map(Faultload::new)
        .toList();
  }
}
