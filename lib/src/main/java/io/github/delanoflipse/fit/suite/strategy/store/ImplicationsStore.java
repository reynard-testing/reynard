package io.github.delanoflipse.fit.suite.strategy.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.strategy.util.Pair;
import io.github.delanoflipse.fit.suite.strategy.util.Sets;
import io.github.delanoflipse.fit.suite.strategy.util.Simplify;

public class ImplicationsStore {
  private static final Logger logger = LoggerFactory.getLogger(ImplicationsStore.class);

  private final List<UpstreamEffect> upstreamEffects = new ArrayList<>();
  private final List<Substitution> inclusions = new ArrayList<>();
  private final List<Substitution> exclusions = new ArrayList<>();
  private final List<DownstreamEffect> downstreamEffects = new ArrayList<>();

  record UpstreamEffect(FaultUid cause, Set<FaultUid> effects) {
  }

  record DownstreamEffect(Set<Behaviour> causes, Behaviour effect) {
  }

  record Substitution(Set<Behaviour> causes, FaultUid effect) {
  }

  public boolean hasUpstreamEffect(FaultUid cause) {
    return upstreamEffects.stream().anyMatch(x -> x.cause.matches(cause));
  }

  public boolean addUpstreamEffect(FaultUid cause, Collection<FaultUid> effects) {
    if (!cause.isNormalForm()) {
      throw new IllegalArgumentException("Upstream cause must be in normal form!");
    }

    for (var effect : effects) {
      if (!effect.isNormalForm()) {
        throw new IllegalArgumentException("Upstream effect must be in normal form!");
      }

      if (!effect.getParent().matches(cause)) {
        throw new IllegalArgumentException("Upstream cause must be a parent of the effect!");
      }
    }

    if (hasUpstreamEffect(cause)) {
      return false;
    }

    upstreamEffects.add(new UpstreamEffect(cause, Set.copyOf(effects)));
    return true;
  }

  public boolean hasDownstreamEffect(Set<Behaviour> causes, Behaviour effect) {
    return downstreamEffects.stream()
        .anyMatch(x -> x.effect.matches(effect) && Behaviour.isSubsetOf(x.causes, causes));
  }

  public boolean addDownstreamEffect(Collection<Behaviour> causes, Behaviour effect) {
    if (!effect.isFault()) {
      throw new IllegalArgumentException("Downstream cause must be a fault!");
    }

    if (!effect.uid().isNormalForm()) {
      throw new IllegalArgumentException("Downstream cause must be in normal form!");
    }

    for (var cause : causes) {
      if (!cause.uid().isNormalForm()) {
        throw new IllegalArgumentException("Downstream effect must be in normal form!");
      }

      if (!cause.uid().getParent().matches(effect.uid())) {
        throw new IllegalArgumentException("Downstream effect must be a parent of the cause(s)!");
      }
    }
    Set<Behaviour> causesSet = Set.copyOf(causes);
    if (hasDownstreamEffect(causesSet, effect)) {
      return false;
    }

    downstreamEffects.add(new DownstreamEffect(causesSet, effect));
    return true;
  }

  private boolean hasEffect(Set<Behaviour> causes, FaultUid effect, List<Substitution> target) {
    return target.stream()
        .anyMatch(x -> x.effect.matches(effect) && Behaviour.isSubsetOf(x.causes, causes));
  }

  private boolean addEffect(Collection<Behaviour> causes, FaultUid effect, List<Substitution> target) {
    if (causes.isEmpty()) {
      throw new IllegalArgumentException("Must have at least one cause!");
    }

    if (!effect.isNormalForm()) {
      throw new IllegalArgumentException("Effect " + effect + " is not in normal form!");
    }

    FaultUid commonParent = effect.getParent();

    for (var cause : causes) {
      if (!cause.uid().isNormalForm()) {
        throw new IllegalArgumentException("Cause " + cause + " is not in normal form!");
      }

      if (!cause.uid().getParent().matches(commonParent)) {
        throw new IllegalArgumentException("Effect and causes must share a common parent! Cause " + cause
            + " does not share common parent " + commonParent);
      }
    }

    Set<Behaviour> causesSet = Set.copyOf(causes);
    if (hasEffect(causesSet, effect, target)) {
      return false;
    }

    // Remove supersets
    target.removeIf(x -> x.effect.matches(effect) && Behaviour.isSubsetOf(causesSet, x.causes));

    // Add myself
    target.add(new Substitution(causesSet, effect));
    return true;
  }

  public boolean addInclusionEffect(Collection<Behaviour> causes, FaultUid addition) {
    return addEffect(causes, addition, inclusions);
  }

  public boolean addExclusionEffect(Collection<Behaviour> causes, FaultUid removal) {
    return addEffect(causes, removal, exclusions);
  }

  public FaultUid getRootCause() {
    for (var upstream : upstreamEffects) {
      if (upstream.cause.isInitial()) {
        return upstream.cause;
      }
    }

    return null;
  }

  public Set<Behaviour> getBehaviours(Collection<Fault> pertubations) {
    var root = getRootCause();

    if (root == null) {
      return Set.of();
    }

    var pair = unfold(getRootCause(), pertubations);
    return Sets.plus(pair.second(), pair.first());
  }

  public Set<Behaviour> getBehaviours(FaultUid cause, Collection<Fault> pertubations) {
    var pair = unfold(cause, pertubations);
    return Sets.plus(pair.second(), pair.first());
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

  private Fault getPertubation(FaultUid cause, Collection<Fault> pertubations) {
    return pertubations.stream()
        .filter(f -> f.uid().matches(cause))
        .findFirst()
        .orElse(null);
  }

  private UpstreamEffect getUpstreamEffect(FaultUid cause) {
    return upstreamEffects.stream()
        .filter(x -> x.cause.matches(cause))
        .findFirst()
        .orElse(null);
  }

  private DownstreamEffect getDownstreamEffect(FaultUid cause, Set<Behaviour> upstreams) {
    return downstreamEffects.stream()
        .filter(x -> x.effect.uid().matches(cause))
        .filter(x -> Behaviour.isSubsetOf(x.causes, upstreams))
        .findFirst()
        .orElse(null);
  }

  private Map<Behaviour, Set<Behaviour>> applySubstitutions(Map<Behaviour, Set<Behaviour>> upstream,
      Collection<Fault> pertubations) {
    // store effects by fault uid, and seperate set of upstreams
    Map<FaultUid, Set<Behaviour>> effects = new HashMap<>();
    Set<Behaviour> upstreams = new LinkedHashSet<>();

    for (var entry : upstream.entrySet()) {
      var cause = entry.getKey();
      var effect = entry.getValue();
      upstreams.add(cause);
      effects.put(cause.uid(), effect);
    }

    Set<Substitution> exclusionsToApply = new LinkedHashSet<>(exclusions);
    Set<Substitution> inclusionsToApply = new LinkedHashSet<>(inclusions);

    while (!exclusionsToApply.isEmpty() || !inclusionsToApply.isEmpty()) {
      boolean changed = false;

      // 2.a. apply exclusions
      var exclusionsIterator = exclusionsToApply.iterator();
      while (exclusionsIterator.hasNext()) {
        var subst = exclusionsIterator.next();
        if (Behaviour.isSubsetOf(subst.causes, upstreams)) {
          // apply substitution
          var effect = subst.effect;
          upstreams.removeIf(u -> u.uid().matches(effect));
          effects.remove(effect);

          exclusionsIterator.remove();
          changed = true;
        }
      }

      // 2.b. apply inclusions
      var inclusionsIterator = inclusionsToApply.iterator();
      while (inclusionsIterator.hasNext()) {
        var subst = inclusionsIterator.next();

        if (Behaviour.isSubsetOf(subst.causes, upstreams)) {
          // apply substitution
          var effect = subst.effect;
          var pair = unfold(effect, pertubations);
          upstreams.add(pair.first());
          effects.put(pair.first().uid(), pair.second());

          inclusionsIterator.remove();
          changed = true;
        }
      }

      if (!changed) {
        break;
      }
    }

    // Convert back to behaviour
    Map<Behaviour, Set<Behaviour>> result = new HashMap<>();
    for (var up : upstreams) {
      result.put(up, effects.getOrDefault(up.uid(), Set.of()));
    }

    return result;
  }

  private Pair<Behaviour, Set<Behaviour>> unfold(FaultUid cause, Collection<Fault> pertubations) {
    // -- Stage 1 - Unfold --
    // 1.a. Directly pertubated, prevents any upstream effects
    Fault pertubation = getPertubation(cause, pertubations);
    if (pertubation != null) {
      Behaviour fault = new Behaviour(cause, pertubation.mode());
      return Pair.of(fault, Set.of());
    }

    // Find upstream effects
    Behaviour causeBehaviour = Behaviour.of(cause);
    UpstreamEffect upstream = getUpstreamEffect(cause);

    // 1.b. No upstream effects, so we are done
    if (upstream == null) {
      return Pair.of(causeBehaviour, Set.of());
    }

    // 1.c. Unfold upstream effects
    Map<Behaviour, Set<Behaviour>> unfoldedUpstream = new HashMap<>();

    // 1. assume all upstream effects are performed
    for (var effect : upstream.effects()) {
      var pair = unfold(effect, pertubations);
      unfoldedUpstream.put(pair.first(), pair.second());
    }

    // -- Stage 2 - Apply substitutions --
    Map<Behaviour, Set<Behaviour>> substitutedUpstream = applySubstitutions(unfoldedUpstream, pertubations);
    Set<Behaviour> directUpstreams = substitutedUpstream.keySet();
    Set<Behaviour> transativeUpstreams = new LinkedHashSet<>();
    for (var entry : substitutedUpstream.entrySet()) {
      transativeUpstreams.add(entry.getKey());
      transativeUpstreams.addAll(entry.getValue());
    }

    // -- Stage 3 - Downstream effects --

    // 3. check for downstream effects
    DownstreamEffect downstream = getDownstreamEffect(cause, directUpstreams);

    if (downstream != null) {
      return Pair.of(downstream.effect(), transativeUpstreams);
    }

    return Pair.of(causeBehaviour, transativeUpstreams);
  }

  public Map<String, Object> getReport(DynamicAnalysisStore store) {
    Map<String, Object> report = new LinkedHashMap<>();

    if (!upstreamEffects.isEmpty()) {
      report.put("Upstream effects", upstreamEffects.size());

      for (var upstream : upstreamEffects) {
        report.put(upstream.cause.toString(), upstream.effects().toString());
      }
    }

    if (!inclusions.isEmpty()) {
      report.put("inclusions", inclusions.size());

      Map<FaultUid, List<Substitution>> groupedInclusions = inclusions.stream()
          .collect(Collectors.groupingBy(Substitution::effect));

      for (var entry : groupedInclusions.entrySet()) {
        List<Set<Behaviour>> causes = entry.getValue().stream()
            .map(x -> x.causes)
            .toList();

        var simplified = Simplify.simplifyBehaviour(causes, store.getModes());
        var i = 0;

        for (Set<FaultUid> cause : simplified.second()) {
          String key = "[" + i + "] " + entry.getKey();
          report.put(key, cause.toString() + " (any failure mode)");
          i++;
        }

        for (Set<Behaviour> cause : simplified.first()) {
          String key = "[" + i + "] " + entry.getKey();
          report.put(key, cause.toString());
          i++;
        }
      }
    }

    if (!exclusions.isEmpty()) {
      report.put("exclusions", exclusions.size());

      Map<FaultUid, List<Substitution>> groupedExclusions = exclusions.stream()
          .collect(Collectors.groupingBy(Substitution::effect));

      for (var entry : groupedExclusions.entrySet()) {
        List<Set<Behaviour>> causes = entry.getValue().stream()
            .map(x -> x.causes)
            .toList();

        var simplified = Simplify.simplifyBehaviour(causes, store.getModes());
        var i = 0;

        for (Set<FaultUid> cause : simplified.second()) {
          String key = "[" + i + "] " + entry.getKey();
          report.put(key, cause.toString() + " (any failure mode)");
          i++;
        }

        for (Set<Behaviour> cause : simplified.first()) {
          String key = "[" + i + "] " + entry.getKey();
          report.put(key, cause.toString());
          i++;
        }
      }
    }

    if (!downstreamEffects.isEmpty()) {
      report.put("Downstream effects", downstreamEffects.size());
    }

    return report;
  }

}
