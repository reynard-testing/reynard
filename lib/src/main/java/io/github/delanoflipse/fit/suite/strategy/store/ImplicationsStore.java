package io.github.delanoflipse.fit.suite.strategy.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.strategy.util.Simplify;

public class ImplicationsStore {
  private static final Logger logger = LoggerFactory.getLogger(ImplicationsStore.class);

  private final List<DownstreamRequestEffect> downstreamRequests = new ArrayList<>();
  private final List<Substitution> inclusions = new ArrayList<>();
  private final List<Substitution> exclusions = new ArrayList<>();
  private final List<UpstreamResponseEffect> upstreamResponses = new ArrayList<>();

  public record DownstreamRequestEffect(FaultUid cause, Set<FaultUid> effects) {
  }

  public record UpstreamResponseEffect(Set<Behaviour> causes, Behaviour effect) {
  }

  public record Substitution(Set<Behaviour> causes, FaultUid effect) {
  }

  // --- Normalisation ---
  public void assertNormalForm(FaultUid cause) {
    if (!cause.isNormalForm()) {
      throw new IllegalArgumentException("Must be in normal form!");
    }
  }

  public void assertNormalForm(Behaviour cause) {
    assertNormalForm(cause.uid());
  }

  public void assertFault(Behaviour cause) {
    if (!cause.isFault()) {
      throw new IllegalArgumentException("Must be a fault!");
    }
  }

  public void assertSameOrigin(FaultUid cause, FaultUid effect) {
    if (!cause.getParent().matches(effect.getParent())) {
      throw new IllegalArgumentException("Must share a common parent!");
    }
  }

  public void assertIsCausedBy(FaultUid cause, FaultUid effect) {
    if (!cause.matches(effect.getParent())) {
      throw new IllegalArgumentException("Must share a common parent!");
    }
  }

  public void assertIsCausedBy(Behaviour cause, Behaviour effect) {
    assertIsCausedBy(cause.uid(), effect.uid());
  }

  // --- Downstream Requests ---
  public boolean hasDownstreamRequests(FaultUid cause) {
    return downstreamRequests.stream().anyMatch(x -> x.cause.matches(cause));
  }

  public boolean addDownstreamRequests(FaultUid cause, Collection<FaultUid> effects) {
    assertNormalForm(cause);

    for (var effect : effects) {
      assertNormalForm(effect);
      assertIsCausedBy(cause, effect);
    }

    if (hasDownstreamRequests(cause)) {
      return false;
    }

    var normalisedEffects = effects.stream()
        .collect(Collectors.toSet());

    downstreamRequests.add(new DownstreamRequestEffect(cause, normalisedEffects));
    return true;
  }

  // --- Upstream Responses ---
  public boolean hasUpstreamResponse(Set<Behaviour> causes, Behaviour effect) {
    return upstreamResponses.stream()
        .anyMatch(x -> x.effect.matches(effect) && Behaviour.isSubsetOf(x.causes, causes));
  }

  public boolean addUpstreamResponse(Collection<Behaviour> causes, Behaviour effect) {
    assertNormalForm(effect);
    assertFault(effect);

    for (var cause : causes) {
      assertIsCausedBy(effect, cause);
    }

    var normalizedCauses = causes.stream()
        .collect(Collectors.toSet());

    if (hasUpstreamResponse(normalizedCauses, effect)) {
      return false;
    }

    upstreamResponses.add(new UpstreamResponseEffect(normalizedCauses, effect));
    return true;
  }

  // --- Inclusions and Exclusions ---
  private boolean hasEffect(Set<Behaviour> causes, FaultUid effect, List<Substitution> target) {
    return target.stream()
        .anyMatch(x -> x.effect.matches(effect) && Behaviour.isSubsetOf(x.causes, causes));
  }

  private boolean addEffect(Collection<Behaviour> causes, FaultUid effect, List<Substitution> target) {
    if (causes.isEmpty()) {
      throw new IllegalArgumentException("Must have at least one cause!");
    }

    assertNormalForm(effect);

    FaultUid commonParent = effect.getParent();

    for (var cause : causes) {
      assertNormalForm(cause);
      assertIsCausedBy(commonParent, cause.uid());
    }

    Set<Behaviour> normalisedCauses = causes.stream()
        .collect(Collectors.toSet());

    if (hasEffect(normalisedCauses, effect, target)) {
      return false;
    }

    if (Behaviour.contains(normalisedCauses, effect)) {
      throw new IllegalArgumentException("Effect " + effect + " is a cause!");
    }

    // Remove supersets
    target.removeIf(x -> x.effect.matches(effect) && Behaviour.isSubsetOf(normalisedCauses, x.causes));

    // Add myself
    target.add(new Substitution(normalisedCauses, effect));
    return true;
  }

  public boolean addInclusionEffect(Collection<Behaviour> causes, FaultUid addition) {
    return addEffect(causes, addition, inclusions);
  }

  public boolean addExclusionEffect(Collection<Behaviour> causes, FaultUid removal) {
    return addEffect(causes, removal, exclusions);
  }

  public boolean isInclusionEffect(FaultUid point) {
    for (var inclusion : inclusions) {
      if (inclusion.effect.matches(point)) {
        return true;
      }
    }

    return false;
  }

  public boolean isAnyInclusionCause(Behaviour point) {
    for (var inclusion : inclusions) {
      if (inclusion.causes.stream().anyMatch(x -> x.matches(point))) {
        return true;
      }
    }

    return false;
  }

  public DownstreamRequestEffect findDownstream(Predicate<DownstreamRequestEffect> predicate) {
    return downstreamRequests.stream()
        .filter(x -> predicate.test(x))
        .findFirst()
        .orElse(null);
  }

  public UpstreamResponseEffect findUpstream(Predicate<UpstreamResponseEffect> predicate) {
    return upstreamResponses.stream()
        .filter(x -> predicate.test(x))
        .findFirst()
        .orElse(null);
  }

  public List<Substitution> findInclusions(Predicate<Substitution> predicate) {
    return inclusions.stream()
        .filter(x -> predicate.test(x))
        .sorted((a, b) -> Integer.compare(a.causes.size(), b.causes.size()))
        .toList();
  }

  public List<Substitution> findExclusions(Predicate<Substitution> predicate) {
    return exclusions.stream()
        .filter(x -> predicate.test(x))
        .sorted((a, b) -> Integer.compare(a.causes.size(), b.causes.size()))
        .toList();
  }

  public FaultUid getRootCause() {
    for (var upstream : downstreamRequests) {
      if (upstream.cause.isInitial()) {
        return upstream.cause;
      }
    }

    return null;
  }

  private Map<String, Object> reportOf(List<Substitution> substitutions, DynamicAnalysisStore store) {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("count", substitutions.size());

    List<Map<String, Object>> fullList = substitutions.stream()
        .map(x -> {
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("causes_names", x.causes.stream().map(Behaviour::toString).toList());
          entry.put("effect_name", x.effect.toString());
          return entry;
        })
        .toList();
    report.put("list", fullList);

    Map<FaultUid, List<Substitution>> grouped = inclusions.stream()
        .collect(Collectors.groupingBy(Substitution::effect));

    List<Map<String, Object>> simplifiedList = new ArrayList<>();

    for (var entry : grouped.entrySet()) {
      List<Set<Behaviour>> causes = entry.getValue().stream()
          .map(x -> x.causes)
          .toList();

      var simplified = Simplify.simplifyBehaviour(causes, store.getModes());

      for (Set<FaultUid> cause : simplified.second()) {
        Map<String, Object> causeReport = new LinkedHashMap<>();
        causeReport.put("any_failure_mode", true);
        causeReport.put("effect_name", entry.getKey().toString());
        causeReport.put("causes_name", cause.toString());
        simplifiedList.add(causeReport);
      }

      for (Set<Behaviour> cause : simplified.first()) {
        Map<String, Object> causeReport = new LinkedHashMap<>();
        causeReport.put("effect_name", entry.getKey().toString());
        causeReport.put("causes_name", cause.toString());
        simplifiedList.add(causeReport);
      }
    }
    report.put("simplified", simplifiedList);
    return report;
  }

  public Map<String, Object> getReport(DynamicAnalysisStore store) {
    Map<String, Object> report = new LinkedHashMap<>();

    if (!downstreamRequests.isEmpty()) {
      Map<String, Object> downstreamReport = new LinkedHashMap<>();
      downstreamReport.put("count", downstreamRequests.size());

      List<Map<String, Object>> downstreams = downstreamRequests.stream()
          .map(x -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("cause_name", x.cause.toString());
            entry.put("effect_names", x.effects.stream().map(FaultUid::toString).toList());
            return entry;
          })
          .toList();

      downstreamReport.put("all", downstreams);
      report.put("downstream", downstreamReport);
    }

    if (!inclusions.isEmpty()) {
      report.put("inclusions", reportOf(inclusions, store));
    }

    if (!exclusions.isEmpty()) {
      report.put("exclusions", reportOf(exclusions, store));
    }

    if (!upstreamResponses.isEmpty()) {
      Map<String, Object> upstreamReport = new LinkedHashMap<>();
      upstreamReport.put("count", upstreamResponses.size());

      List<Map<String, Object>> upstreams = upstreamResponses.stream()
          .map(x -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("effect_name", x.effect.toString());
            entry.put("causes_names", x.causes.stream().map(Behaviour::toString).toList());
            return entry;
          })
          .toList();

      upstreamReport.put("all", upstreams);
      report.put("upstream", upstreamReport);
    }

    return report;
  }
}
