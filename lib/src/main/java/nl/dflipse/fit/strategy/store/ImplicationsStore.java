package nl.dflipse.fit.strategy.store;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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

  private final Set<UpstreamEffects> upstreamEffects = new HashSet<>();
  private final Set<Substitution> substitutions = new HashSet<>();
  private final Set<DownstreamEffect> downstreamEffects = new HashSet<>();

  record UpstreamEffects(Behaviour cause, Set<Behaviour> effects) {
  }

  record DownstreamEffect(Set<Behaviour> causes, Behaviour effect) {
  }

  enum SubstitutionType {
    INCLUSION,
    EXCLUSION
  }

  record Substitution(Set<Behaviour> causes, Set<Behaviour> effects, SubstitutionType type) {
  }

  public void addUpstreamCause(Behaviour cause, Collection<Behaviour> effects) {
    upstreamEffects.add(new UpstreamEffects(cause, Set.copyOf(effects)));
  }

  public void addDownstreamCause(Collection<Behaviour> causes, Behaviour effects) {
    downstreamEffects.add(new DownstreamEffect(Set.copyOf(causes), effects));
  }

  public void addInclusionEffect(Collection<Behaviour> causes, Collection<Behaviour> additions) {
    var causesSet = Set.copyOf(causes);
    var additionsSet = Set.copyOf(additions);
    substitutions.add(new Substitution(causesSet, additionsSet, SubstitutionType.INCLUSION));
  }

  public void addExclusionEffect(Collection<Behaviour> causes, Collection<Behaviour> exclusions) {
    var causesSet = Set.copyOf(causes);
    var exclusionsSet = Set.copyOf(exclusions);
    substitutions.add(new Substitution(causesSet, exclusionsSet, SubstitutionType.EXCLUSION));
  }

  public Set<Behaviour> getBehaviours(FaultUid cause, Collection<Fault> pertubations) {
    Behaviour causeBehaviour = new Behaviour(cause, null);

    var pair = unfold(causeBehaviour, pertubations);
    return Sets.plus(pair.second(), pair.first());
  }

  private Pair<Behaviour, Set<Behaviour>> unfold(Behaviour cause, Collection<Fault> pertubations) {
    // Directly pertubated
    Fault faultPertubation = pertubations.stream()
        .filter(f -> f.uid().matches(cause.uid()))
        .findFirst()
        .orElse(null);

    if (faultPertubation != null) {
      return Pair.of(faultPertubation.asBehaviour(), Set.of());
    }

    UpstreamEffects upstream = upstreamEffects.stream()
        .filter(x -> x.cause().matches(cause))
        .findFirst()
        .orElse(null);

    // leave node
    if (upstream == null) {
      return Pair.of(cause, Set.of());
    }

    Map<FaultUid, Set<Behaviour>> effects = new HashMap<>();
    Set<Behaviour> upstreams = new HashSet<>();

    // 1. assume all upstream effects are performed
    for (var effect : upstream.effects()) {
      var pair = unfold(effect, pertubations);
      upstreams.add(pair.first());
      effects.put(pair.first().uid(), pair.second());
    }

    Set<Substitution> substitutionsToApply = new HashSet<>();
    substitutionsToApply.addAll(substitutions);
    while (!substitutionsToApply.isEmpty()) {
      boolean changed = false;

      // 2. apply substitutions
      for (var subst : substitutionsToApply) {
        if (Behaviour.isSubsetOf(subst.causes, upstreams)) {
          // apply substitution
          switch (subst.type) {
            case INCLUSION -> {
              for (var effect : subst.effects()) {
                var pair = unfold(effect, pertubations);
                upstreams.add(pair.first());
                effects.put(pair.first().uid(), pair.second());
              }
            }

            case EXCLUSION -> {
              for (var effect : subst.effects()) {
                upstreams.remove(effect);
                effects.remove(effect.uid());
              }
            }
          }

          substitutionsToApply.remove(subst);
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
        .filter(x -> x.effect.matches(cause))
        .filter(x -> Behaviour.isSubsetOf(x.causes, upstreams))
        .findFirst()
        .orElse(null);

    if (downstream != null) {
      return Pair.of(downstream.effect(), effectsSet);
    }

    return Pair.of(cause, effectsSet);
  }

}
