package nl.dflipse.fit.strategy.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.util.Pair;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.TransativeRelation;

public class ImplicationsStore {
  private static final Logger logger = LoggerFactory.getLogger(ImplicationsStore.class);
  private final TransativeRelation<Behaviour> happensBefore = new TransativeRelation<>();
  private final Set<DownstreamSubstitution> substitutions = new HashSet<>();
  private final Set<DownstreamEffect> effects = new HashSet<>();

  // !x -> !y
  record DownstreamSubstitution(Set<Behaviour> causes, Set<Behaviour> effects) {
  }

  record DownstreamEffect(Set<Behaviour> causes, Behaviour effects) {
  }

  public void addUpstreamCause(Behaviour cause, Collection<Behaviour> effects) {
    for (var effect : effects) {
      happensBefore.addRelation(cause, effect);
    }
  }

  public void addSubstitution(Set<Behaviour> causes, Set<Behaviour> effects) {
    substitutions.add(new DownstreamSubstitution(causes, effects));
  }

  public void updateUpstreamCauses(Behaviour cause, Collection<Behaviour> effects) {
    for (var effect : effects) {
      Behaviour causeParent = happensBefore.getParent(cause);
      Behaviour effectParent = happensBefore.getParent(effect);
      boolean sameParent = causeParent == null
          ? effectParent == null
          : causeParent.equals(effectParent);
      if (sameParent) {
        happensBefore.removeRelation(causeParent, effect);
        happensBefore.addRelation(cause, effect);
      } else {
        throw new IllegalStateException("Cannot update upstream cause, as the cause and effect have different parents");
      }
    }
  }

  public Set<Behaviour> getBehaviours(FaultUid cause, Collection<Fault> pertubations) {
    Behaviour causeBehaviour = new Behaviour(cause, null);

    var pair = unfold(causeBehaviour, pertubations);
    return Sets.plus(pair.second(), pair.first());
  }

  private Set<Behaviour> applySubstitution(Set<Behaviour> effect) {
    for (var substitution : substitutions) {
      if (Behaviour.isSubsetOf(substitution.causes, effect)) {
        return effect;
      }
    }

    return null;
  }

  private Set<Behaviour> applySubstitutions(Set<Behaviour> behaviours) {
    List<Set<Behaviour>> seenSubstitutions = new ArrayList<>();
    seenSubstitutions.add(behaviours);

    while (true) {
      Set<Behaviour> nextSubstitution = applySubstitution(behaviours);
      if (nextSubstitution == null) {
        break;
      }

      if (seenSubstitutions.contains(nextSubstitution)) {
        logger.warn("Found cyclic substitution: {} -> {}", seenSubstitutions, nextSubstitution);
        break;
      }

      behaviours = nextSubstitution;
    }

    return behaviours;
  }

  private Set<Behaviour> merge(Set<Behaviour> base, Set<Behaviour> extension) {
    var merged = new HashSet<>(base);

    for (var addition : extension) {
      boolean overlap = false;
      for (var existing : merged) {
        if (existing.uid().matches(addition.uid())) {
          merged.remove(existing);
          merged.add(addition);
          overlap = true;
          break;
        }
      }

      if (!overlap) {
        merged.add(addition);
      }
    }

    return merged;
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

    Set<Behaviour> directEffects = new HashSet<Behaviour>();
    Set<Behaviour> downstreamEffects = new HashSet<Behaviour>();

    for (var effect : happensBefore.getChildren(cause)) {
      var pair = unfold(effect, pertubations);
      Behaviour effectBehaviour = pair.first();
      Set<Behaviour> nestedBehaviour = pair.second();

      unfolded = merge(unfolded, unfold(effect, pertubations));
    }

    if (unfolded.isEmpty()) {
      return Set.of(cause);
    }

    unfolded = applySubstitutions(unfolded);
    return unfolded;
  }

}
