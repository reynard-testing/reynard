package io.github.delanoflipse.fit.suite.strategy.store;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.strategy.store.ImplicationsStore.DownstreamRequestEffect;
import io.github.delanoflipse.fit.suite.strategy.store.ImplicationsStore.Substitution;
import io.github.delanoflipse.fit.suite.strategy.store.ImplicationsStore.UpstreamResponseEffect;
import io.github.delanoflipse.fit.suite.strategy.util.Pair;
import io.github.delanoflipse.fit.suite.strategy.util.Sets;
import io.github.delanoflipse.fit.suite.strategy.util.TransativeRelation;

public class ImplicationsModel {
    private final ImplicationsStore store;

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

    private int maxSize(List<Substitution> lst) {
        return lst.stream().mapToInt(x -> x.causes().size()).max().orElse(0);
    }

    private Map<Behaviour, Set<Behaviour>> applySubstitutions(Map<Behaviour, Set<Behaviour>> upstream,
            Collection<Fault> pertubations) {
        // store effects by fault uid, and seperate set of upstreams
        Map<FaultUid, Set<Behaviour>> effects = new HashMap<>();
        Set<Behaviour> upstreams = new LinkedHashSet<>();
        FaultUid root = null;
        for (var entry : upstream.entrySet()) {
            var cause = entry.getKey();
            var effect = entry.getValue();
            upstreams.add(cause);
            effects.put(cause.uid(), effect);

            if (root == null) {
                root = cause.uid().getParent();
            }
        }

        TransativeRelation<FaultUid> dependsOn = new TransativeRelation<>();

        List<Substitution> exclusionsToApply = store.getRelatedExclusions(root);
        List<Substitution> inclusionsToApply = store.getRelatedInclusions(root);
        int maxSize = Math.max(maxSize(exclusionsToApply), maxSize(inclusionsToApply));

        Consumer<FaultUid> exclude = (f) -> {
            upstreams.removeIf(u -> u.uid().matches(f));
            effects.remove(f);
        };

        Consumer<FaultUid> include = (f) -> {
            var pair = unfold(f, pertubations);
            upstreams.add(pair.first());
            effects.put(pair.first().uid(), pair.second());
        };

        for (int i = 0; i < maxSize; i++) {
            for (var subst : exclusionsToApply) {
                if (subst.causes().size() != i) {
                    continue;
                }

                if (Behaviour.isSubsetOf(subst.causes(), upstreams)) {
                    // apply substitution
                    var effect = subst.effect();
                    exclude.accept(effect);

                    // Exclude all related exclusions
                    dependsOn.getDecendants(effect).stream()
                            .forEach(exclude);

                }
            }

            for (var subst : inclusionsToApply) {
                if (subst.causes().size() != i) {
                    continue;
                }

                if (Behaviour.isSubsetOf(subst.causes(), upstreams)) {
                    // apply substitution
                    var effect = subst.effect();
                    include.accept(effect);

                    // Create relation
                    for (var c : subst.causes()) {
                        dependsOn.addRelation(c.uid(), effect);
                    }
                }
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
        DownstreamRequestEffect upstream = store.getUpstreamEffect(cause);

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
        UpstreamResponseEffect downstream = store.getDownstreamEffect(cause, directUpstreams);

        if (downstream != null) {
            return Pair.of(downstream.effect(), transativeUpstreams);
        }

        return Pair.of(causeBehaviour, transativeUpstreams);
    }
}
