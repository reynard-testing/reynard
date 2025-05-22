package io.github.delanoflipse.fit.suite.strategy.store;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.strategy.store.ImplicationsStore.DownstreamRequestEffect;
import io.github.delanoflipse.fit.suite.strategy.store.ImplicationsStore.Substitution;
import io.github.delanoflipse.fit.suite.strategy.store.ImplicationsStore.UpstreamResponseEffect;
import io.github.delanoflipse.fit.suite.strategy.util.Lists;
import io.github.delanoflipse.fit.suite.strategy.util.Pair;
import io.github.delanoflipse.fit.suite.strategy.util.Sets;
import io.github.delanoflipse.fit.suite.strategy.util.TransativeRelation;

public class ImplicationsModel {
    private final ImplicationsStore store;
    private static final Logger logger = LoggerFactory.getLogger(ImplicationsModel.class);

    public ImplicationsModel(ImplicationsStore store) {
        this.store = store;
    }

    private Fault getPertubation(FaultUid cause, Collection<Fault> pertubations) {
        return pertubations.stream()
                .filter(f -> f.uid().matches(cause))
                .findFirst()
                .orElse(null);
    }

    public Set<Behaviour> getBehaviours(Collection<Fault> pertubations) {
        var root = store.getRootCause();

        if (root == null) {
            return Set.of();
        }

        var pair = unfold(root, pertubations);
        return Sets.plus(pair.second(), pair.first());
    }

    public Set<Behaviour> getBehaviours(FaultUid cause, Collection<Fault> pertubations) {
        var pair = unfold(cause, pertubations);
        return Sets.plus(pair.second(), pair.first());
    }

    private List<FaultUid> evaluationOrder(List<Substitution> exclusionsToApply, List<Substitution> inclusionsToApply) {
        // Build a lattice of substition relations wrt points
        TransativeRelation<FaultUid> dependsOn = new TransativeRelation<>();
        List<Substitution> allSubs = Lists.union(exclusionsToApply, inclusionsToApply);
        for (var s : allSubs) {
            for (var cause : s.causes()) {
                try {
                    dependsOn.addRelation(cause.uid(), s.effect());
                } catch (Exception e) {
                    logger.warn("Found a circular substitution dependency, likely due to indistinguishable events: {}",
                            s);
                }
            }
        }

        // a return the topological order
        // The goal is to evaluate dependencies in order
        return dependsOn.topologicalOrder();
    }

    private Map<Behaviour, Set<Behaviour>> applySubstitutions(Map<Behaviour, Set<Behaviour>> upstream,
            Collection<Fault> pertubations) {
        // store effects by fault uid, and seperate set of upstreams
        Map<FaultUid, Set<Behaviour>> downstreamEffects = new HashMap<>();
        Set<Behaviour> downstreams = new LinkedHashSet<>();

        for (var entry : upstream.entrySet()) {
            var cause = entry.getKey();
            var effect = entry.getValue();
            downstreams.add(cause);
            downstreamEffects.put(cause.uid(), effect);
        }

        // Get substitutions related to origin
        FaultUid origin = downstreams.iterator().next().uid().getParent();
        List<Substitution> exclusionsToApply = store.getRelatedExclusions(origin);
        List<Substitution> inclusionsToApply = store.getRelatedInclusions(origin);

        // In order or substitution dependencies (or as good as possible)
        var orderedPoints = evaluationOrder(exclusionsToApply, inclusionsToApply);
        for (var point : orderedPoints) {
            // Are we already pretending to be including it?
            boolean shouldInclude = downstreams.stream()
                    .anyMatch(x -> x.uid().matches(point));

            // If not, check if we have reasons to include it
            if (!shouldInclude) {
                for (var subst : inclusionsToApply) {
                    if (!subst.effect().matches(point)) {
                        continue;
                    }

                    if (Behaviour.isSubsetOf(subst.causes(), downstreams)) {
                        shouldInclude = true;
                        break;
                    }
                }
            }

            // If we have reasons to include it
            // Check if there are better reasons to exclude it
            if (shouldInclude) {
                for (var subst : exclusionsToApply) {
                    if (!subst.effect().matches(point)) {
                        continue;
                    }

                    if (Behaviour.isSubsetOf(subst.causes(), downstreams)) {
                        shouldInclude = false;
                        break;
                    }
                }
            }

            // We have reached a conclusion
            if (shouldInclude) {
                var pair = unfold(point, pertubations);
                downstreams.add(pair.first());
                downstreamEffects.put(pair.first().uid(), pair.second());
            } else {
                downstreams.removeIf(u -> u.uid().matches(point));
                downstreamEffects.remove(point);
            }
        }

        // Convert back to behaviour
        Map<Behaviour, Set<Behaviour>> result = new HashMap<>();
        for (var down : downstreams) {
            result.put(down, downstreamEffects.getOrDefault(down.uid(), Set.of()));
        }

        return result;
    }

    private Pair<Behaviour, Set<Behaviour>> unfold(FaultUid cause, Collection<Fault> pertubations) {
        // -- Stage 1 - Unfold --
        // 1.a. Directly pertubated, prevents any downstream effects
        Fault pertubation = getPertubation(cause, pertubations);
        if (pertubation != null) {
            Behaviour fault = new Behaviour(cause, pertubation.mode());
            return Pair.of(fault, Set.of());
        }

        // Find downstream effects
        Behaviour causeBehaviour = Behaviour.of(cause);
        DownstreamRequestEffect downstream = store.getDownstream(cause);

        // 1.b. No downstream effects, so we are done
        if (downstream == null) {
            return Pair.of(causeBehaviour, Set.of());
        }

        // 1.c. Unfold downstream effects
        // assume all downstream requests are performed
        Map<Behaviour, Set<Behaviour>> unfoldedDownstream = new HashMap<>();
        for (var effect : downstream.effects()) {
            var pair = unfold(effect, pertubations);
            unfoldedDownstream.put(pair.first(), pair.second());
        }

        // -- Stage 2 - Apply substitutions --
        Map<Behaviour, Set<Behaviour>> substitutedUpstream = applySubstitutions(unfoldedDownstream, pertubations);
        Set<Behaviour> directDownstreams = substitutedUpstream.keySet();
        Set<Behaviour> transativeDownstreams = new LinkedHashSet<>();
        for (var entry : substitutedUpstream.entrySet()) {
            transativeDownstreams.add(entry.getKey());
            transativeDownstreams.addAll(entry.getValue());
        }

        // -- Stage 3 - Upstream effects --
        // 3. check for upstream effects
        UpstreamResponseEffect upstream = store.getUpstream(cause, directDownstreams);

        if (upstream != null) {
            return Pair.of(upstream.effect(), transativeDownstreams);
        }

        // assume the happy path behaviour
        return Pair.of(causeBehaviour, transativeDownstreams);
    }
}
