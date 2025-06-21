package dev.reynard.junit.strategy.store;

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

import dev.reynard.junit.faultload.Behaviour;
import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultInjectionPoint;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.strategy.util.Simplify;
import dev.reynard.junit.strategy.util.TransativeRelation;

public class ImplicationsStore {
  private static final Logger logger = LoggerFactory.getLogger(ImplicationsStore.class);

  private final Map<FaultInjectionPoint, TransativeRelation<FaultInjectionPoint>> implicationDependencies = new LinkedHashMap<>();

  private final List<DownstreamRequestEffect> downstreamRequests = new ArrayList<>();
  private final LookupList<FaultInjectionPoint, Substitution> inclusions = new LookupList<>(
      this::getLookupKey);
  private final LookupList<FaultInjectionPoint, Substitution> exclusions = new LookupList<>(
      this::getLookupKey);
  private final LookupList<FaultInjectionPoint, UpstreamResponseEffect> upstreamResponses = new LookupList<>(
      this::getLookupKey);

  public record DownstreamRequestEffect(FaultUid cause, Set<FaultUid> effects) {
  }

  public record UpstreamResponseEffect(Set<Behaviour> causes, Behaviour effect) {
  }

  public record Substitution(Set<Behaviour> causes, FaultUid effect) {
  }

  private FaultInjectionPoint getLookupKey(UpstreamResponseEffect b) {
    return getLookupKey(b.effect());
  }

  private FaultInjectionPoint getLookupKey(Substitution x) {
    return getLookupKey(x.effect.getParent());
  }

  private FaultInjectionPoint getLookupKey(Behaviour b) {
    return getLookupKey(b.uid());
  }

  private FaultInjectionPoint getLookupKey(FaultUid uid) {
    return getLookupKey(uid.getPoint());
  }

  private FaultInjectionPoint getLookupKey(FaultInjectionPoint x) {
    return x.asAnyCount().asAnyPredecessors();
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
    return upstreamResponses.get(getLookupKey(effect)).stream()
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
  private boolean hasEffect(Set<Behaviour> causes, FaultUid effect,
      LookupList<FaultInjectionPoint, Substitution> target) {
    return target
        .get(getLookupKey(effect))
        .stream()
        .anyMatch(x -> x.effect.matches(effect) && Behaviour.isSubsetOf(x.causes, causes));
  }

  private boolean addEffect(Collection<Behaviour> causes, FaultUid effect,
      LookupList<FaultInjectionPoint, Substitution> target) {
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
    for (var inclusion : inclusions.get(getLookupKey(point))) {
      if (inclusion.effect.matches(point)) {
        return true;
      }
    }

    return false;
  }

  public boolean isAnyInclusionCause(Behaviour point) {
    for (var inclusion : inclusions.get(getLookupKey(point))) {
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

  public UpstreamResponseEffect findUpstream(FaultInjectionPoint k, Predicate<UpstreamResponseEffect> predicate) {
    return upstreamResponses.get(getLookupKey(k)).stream()
        .filter(x -> predicate.test(x))
        .findFirst()
        .orElse(null);
  }

  public List<Substitution> findInclusions(FaultInjectionPoint k, Predicate<Substitution> predicate) {
    return inclusions.get(getLookupKey(k)).stream()
        .filter(x -> predicate.test(x))
        // TODO: ensure list is ordered by size of causes, instead of sorting here
        .sorted((a, b) -> Integer.compare(b.causes.size(), a.causes.size()))
        .toList();
  }

  public List<Substitution> findExclusions(FaultInjectionPoint k, Predicate<Substitution> predicate) {
    return exclusions.get(getLookupKey(k)).stream()
        .filter(x -> predicate.test(x))
        // TODO: ensure list is ordered by size of causes, instead of sorting here
        .sorted((a, b) -> Integer.compare(b.causes.size(), a.causes.size()))
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

  private Map<String, Object> reportOf(Behaviour b) {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("uid", reportOf(b.uid()));
    if (b.mode() == null) {
      report.put("mode", null);
    } else {
      report.put("mode", b.mode().toString());
    }
    return report;
  }

  private Object reportOf(FaultUid f) {
    return f.toString();
  }

  private Map<String, Object> reportOf(LookupList<FaultInjectionPoint, Substitution> substitutions,
      DynamicAnalysisStore store) {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("count", substitutions.getAll().size());

    Map<FaultUid, List<Substitution>> grouped = inclusions.getAll().stream()
        .collect(Collectors.groupingBy(Substitution::effect));

    List<Map<String, Object>> simplifiedList = new ArrayList<>();

    for (var entry : grouped.entrySet()) {
      List<Set<Behaviour>> allCauses = entry.getValue().stream()
          .map(x -> x.causes)
          .toList();

      var simplified = Simplify.simplifyBehaviour(allCauses, store.getModes());

      for (Set<FaultUid> causes : simplified.second()) {
        Map<String, Object> causeReport = new LinkedHashMap<>();
        causeReport.put("any_failure_mode", true);
        causeReport.put("effect_name", reportOf(entry.getKey()));
        causeReport.put("causes_name", causes.stream()
            .map(this::reportOf)
            .toList());
        simplifiedList.add(causeReport);
      }

      for (Set<Behaviour> causes : simplified.first()) {
        Map<String, Object> causeReport = new LinkedHashMap<>();
        causeReport.put("effect_name", reportOf(entry.getKey()));
        causeReport.put("causes_name", causes.stream()
            .map(this::reportOf)
            .toList());
        simplifiedList.add(causeReport);
      }
    }
    report.put("simplified", simplifiedList);

    List<Map<String, Object>> fullList = substitutions.getAll().stream()
        .map(x -> {
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("effect_name", reportOf(x.effect));
          entry.put("causes_names", x.causes.stream()
              .map(this::reportOf)
              .toList());
          return entry;
        })
        .toList();
    report.put("list", fullList);

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
            entry.put("cause_name", reportOf(x.cause));
            entry.put("effect_names", x.effects.stream()
                .map(this::reportOf)
                .toList());
            return entry;
          })
          .toList();

      downstreamReport.put("list", downstreams);
      report.put("downstream", downstreamReport);
    }

    if (!inclusions.getAll().isEmpty()) {
      report.put("inclusions", reportOf(inclusions, store));
    }

    if (!exclusions.getAll().isEmpty()) {
      report.put("exclusions", reportOf(exclusions, store));
    }

    if (!upstreamResponses.getAll().isEmpty()) {
      Map<String, Object> upstreamReport = new LinkedHashMap<>();
      upstreamReport.put("count", upstreamResponses.getAll().size());

      List<Map<String, Object>> upstreams = upstreamResponses.getAll().stream()
          .map(x -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("effect_name", reportOf(x.effect));
            entry.put("causes_names", x.causes.stream()
                .map(this::reportOf)
                .toList());
            return entry;
          })
          .toList();

      upstreamReport.put("list", upstreams);
      report.put("upstream", upstreamReport);
    }

    return report;
  }
}
