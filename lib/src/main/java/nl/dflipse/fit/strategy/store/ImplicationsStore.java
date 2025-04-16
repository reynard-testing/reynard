package nl.dflipse.fit.strategy.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.util.Pair;
import nl.dflipse.fit.strategy.util.Sets;

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

  private Pair<Behaviour, Set<Behaviour>> unfold(FaultUid cause, Collection<Fault> pertubations) {
    // Directly pertubated, prevents any upstream effects
    Fault faultPertubation = pertubations.stream()
        .filter(f -> f.uid().matches(cause))
        .findFirst()
        .orElse(null);

    if (faultPertubation != null) {
      return Pair.of(faultPertubation.asBehaviour(), Set.of());
    }

    Behaviour causeBehaviour = Behaviour.of(cause);
    UpstreamEffect upstream = upstreamEffects.stream()
        .filter(x -> x.cause.matches(cause))
        .findFirst()
        .orElse(null);

    // leave node
    if (upstream == null) {
      return Pair.of(causeBehaviour, Set.of());
    }

    Map<FaultUid, Set<Behaviour>> effects = new HashMap<>();
    Set<Behaviour> upstreams = new HashSet<>();

    // 1. assume all upstream effects are performed
    for (var effect : upstream.effects()) {
      var pair = unfold(effect, pertubations);
      upstreams.add(pair.first());
      effects.put(pair.first().uid(), pair.second());
    }

    Set<Substitution> exclusionsToApply = new HashSet<>(exclusions);
    Set<Substitution> inclusionsToApply = new HashSet<>(inclusions);

    while (!exclusionsToApply.isEmpty() || !inclusionsToApply.isEmpty()) {
      boolean changed = false;

      // 2.a. apply exclusions
      for (var subst : exclusionsToApply) {
        if (Behaviour.isSubsetOf(subst.causes, upstreams)) {
          // apply substitution
          var effect = subst.effect;
          upstreams.removeIf(u -> u.uid().matches(effect));
          effects.remove(effect);

          exclusionsToApply.remove(subst);
          changed = true;
          break;
        }
      }

      if (changed) {
        // first apply exclusions
        // then apply inclusions
        continue;
      }

      // 2.b. apply inclusions
      for (var subst : inclusionsToApply) {
        if (Behaviour.isSubsetOf(subst.causes, upstreams)) {
          // apply substitution
          var effect = subst.effect;
          var pair = unfold(effect, pertubations);
          upstreams.add(pair.first());
          effects.put(pair.first().uid(), pair.second());

          inclusionsToApply.remove(subst);
          changed = true;
          break;
        }
      }

      if (!changed) {
        break;
      }
    }

    Set<Behaviour> effectsSet = new HashSet<>();
    effectsSet.addAll(upstreams);
    for (var entry : effects.entrySet()) {
      effectsSet.addAll(entry.getValue());
    }

    // 3. check for downstream effects
    DownstreamEffect downstream = downstreamEffects.stream()
        .filter(x -> x.effect.uid().matches(cause))
        .filter(x -> Behaviour.isSubsetOf(x.causes, upstreams))
        .findFirst()
        .orElse(null);

    if (downstream != null) {
      return Pair.of(downstream.effect(), effectsSet);
    }

    return Pair.of(causeBehaviour, effectsSet);
  }

  public Map<String, String> getReport() {
    Map<String, String> report = new LinkedHashMap<>();

    if (!upstreamEffects.isEmpty()) {
      report.put("Upstream effects", upstreamEffects.size() + "");
    }

    if (!inclusions.isEmpty()) {
      report.put("Inclusion effects", inclusions.size() + "");
    }

    if (!exclusions.isEmpty()) {
      report.put("Exclusion effects", exclusions.size() + "");
    }

    if (!downstreamEffects.isEmpty()) {
      report.put("Downstream effects", downstreamEffects.size() + "");
    }

    return report;
  }

}
