package io.github.delanoflipse.fit.suite.strategy.store;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultInjectionPoint;
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
    private final boolean matchWeak = true;

    public ImplicationsModel(ImplicationsStore store) {
        this.store = store;
    }

    private boolean matchesLocally(Behaviour x, Behaviour y) {
        return matchesLocally(x.uid(), y.uid()) && x.mode().equals(y.mode());
    }

    private boolean matchesLocally(FaultUid x, FaultUid y) {
        if (matchWeak) {
            return x.getPoint().matches(y.getPoint());
        }

        return matchesGlobally(x, y);
    }

    private boolean matchesGlobally(FaultUid x, FaultUid y) {
        return x.matches(y);
    }

    private boolean isLocalSubsetOf(Collection<Behaviour> subset, Collection<Behaviour> superset) {
        return Sets.isSubsetOf(subset, superset, (x, y) -> matchesLocally(x, y));
    }

    private Behaviour getMatchingPertubation(FaultUid cause, Collection<Fault> pertubations) {
        // Find a pertubation that globally matches the cause
        // as this is the logic the proxies use
        Fault perturbed = pertubations.stream()
                .filter(f -> matchesGlobally(f.uid(), cause))
                .findFirst()
                .orElse(null);
        if (perturbed == null) {
            return null;
        }

        return new Behaviour(cause, perturbed.mode());
    }

    private FaultUid reconstruct(FaultUid rootCause, FaultUid child) {
        return rootCause.asChild(child.getPoint());
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

    private List<FaultUid> evaluationOrder(FaultUid rootCause, List<Substitution> exclusionsToApply,
            List<Substitution> inclusionsToApply) {
        // Build a lattice of substition relations wrt points
        TransativeRelation<FaultInjectionPoint> dependsOn = new TransativeRelation<>();
        List<Substitution> allSubs = Lists.union(exclusionsToApply, inclusionsToApply);

        boolean circularDetected = false;
        for (var s : allSubs) {
            for (var cause : s.causes()) {
                try {
                    dependsOn.addRelation(cause.uid().getPoint(), s.effect().getPoint());
                } catch (Exception e) {
                    logger.info("Circular dependency in {} given {}", cause.uid().getPoint(), s.effect().getPoint());
                    circularDetected = true;
                }
            }
        }

        if (circularDetected) {
            logger.warn("Circular dependencies detected in substitutions, this may lead to unexpected results");
        }

        // a return the topological order
        // The goal is to evaluate dependencies in order
        return dependsOn.topologicalOrder().stream()
                // reconstruct the uid to match the root cause
                .map(x -> rootCause.asChild(x))
                .toList();
    }

    private Map<Behaviour, Set<Behaviour>> applySubstitutions(Map<Behaviour, Set<Behaviour>> upstream,
            Collection<Fault> pertubations) {
        // store effects by fault uid, and seperate set of upstreams
        Map<FaultUid, Set<Behaviour>> downstreamEffects = new LinkedHashMap<>();
        Set<Behaviour> downstreams = new LinkedHashSet<>();

        for (var entry : upstream.entrySet()) {
            var cause = entry.getKey();
            var effect = entry.getValue();
            downstreams.add(cause);
            downstreamEffects.put(cause.uid(), effect);
        }

        // Get substitutions related to origin
        FaultUid origin = downstreams.iterator().next().uid().getParent();
        List<Substitution> exclusionsToApply = getRelatedExclusions(origin);
        List<Substitution> inclusionsToApply = getRelatedInclusions(origin);

        // In order or substitution dependencies (or as good as possible)
        var orderedPoints = evaluationOrder(origin, exclusionsToApply, inclusionsToApply);
        for (var point : orderedPoints) {
            // Are we already pretending to be including it?
            boolean shouldInclude = downstreams.stream()
                    .anyMatch(x -> matchesLocally(x.uid(), point));

            // If not, check if we have reasons to include it
            if (!shouldInclude) {
                for (var subst : inclusionsToApply) {
                    if (!matchesLocally(subst.effect(), point)) {
                        continue;
                    }

                    if (isLocalSubsetOf(subst.causes(), downstreams)) {
                        shouldInclude = true;
                        break;
                    }
                }
            }

            // If we have reasons to include it
            // Check if there are better reasons to exclude it
            if (shouldInclude) {
                for (var subst : exclusionsToApply) {
                    if (!matchesLocally(subst.effect(), point)) {
                        continue;
                    }

                    if (isLocalSubsetOf(subst.causes(), downstreams)) {
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
                downstreams.removeIf(u -> matchesLocally(u.uid(), point));
                downstreamEffects.remove(point);
            }
        }

        // Convert back to behaviour
        Map<Behaviour, Set<Behaviour>> result = new LinkedHashMap<>();
        for (var down : downstreams) {
            result.put(down, downstreamEffects.getOrDefault(down.uid(), Set.of()));
        }

        return result;
    }

    private List<FaultUid> getDownstream(FaultUid cause) {
        // all weakly matching downstream requests
        DownstreamRequestEffect downstream = store.findDownstream(x -> matchesLocally(x.cause(), cause));

        if (downstream == null) {
            return null;
        }

        return downstream.effects().stream()
                // reconstruct the uid to match the cause
                .map(x -> reconstruct(cause, x))
                .toList();
    }

    private Behaviour getUpstream(FaultUid cause, Set<Behaviour> upstreams) {
        UpstreamResponseEffect upstream = store.findUpstream(cause.getPoint(),
                x -> matchesLocally(x.effect().uid(), cause) &&
                        Behaviour.isSubsetOf(x.causes(), upstreams));
        if (upstream == null) {
            return null;
        }

        Behaviour upstreamEffect = upstream.effect();
        // reconstruct the uid to match the cause
        return new Behaviour(cause, upstreamEffect.mode());
    }

    private List<Substitution> getRelatedInclusions(FaultUid rootCause) {
        // all inclusions match weakly with the root cause
        return store.findInclusions(x -> matchesLocally(x.effect().getParent(), rootCause));
    }

    private List<Substitution> getRelatedExclusions(FaultUid root) {
        // all exclusions match weakly with the root cause
        return store.findExclusions(x -> matchesLocally(x.effect().getParent(), root));
    }

    private Pair<Behaviour, Set<Behaviour>> unfold(FaultUid cause, Collection<Fault> pertubations) {
        // -- Stage 1 - Unfold --
        // 1.a. Directly pertubated, prevents any downstream effects
        Behaviour pertubation = getMatchingPertubation(cause, pertubations);
        if (pertubation != null) {
            return Pair.of(pertubation, Set.of());
        }

        // Find downstream effects
        Behaviour causeBehaviour = Behaviour.of(cause);
        List<FaultUid> downstream = getDownstream(cause);

        // 1.b. No downstream effects, so we are done
        if (downstream == null) {
            return Pair.of(causeBehaviour, Set.of());
        }

        // 1.c. Unfold downstream effects
        // assume all downstream requests are performed
        Map<Behaviour, Set<Behaviour>> unfoldedDownstream = new LinkedHashMap<>();
        for (var effect : downstream) {
            var pair = unfold(effect, pertubations);
            unfoldedDownstream.put(pair.first(), pair.second());
        }

        // -- Stage 2 - Apply substitutions --
        Map<Behaviour, Set<Behaviour>> substitutedUpstream = applySubstitutions(unfoldedDownstream, pertubations);

        // -- Stage 3 - Upstream effects --
        // 3.a collect direct and transative downstreams
        Set<Behaviour> directDownstreams = substitutedUpstream.keySet();
        Set<Behaviour> transativeDownstreams = new LinkedHashSet<>();
        for (var entry : substitutedUpstream.entrySet()) {
            transativeDownstreams.add(entry.getKey());
            transativeDownstreams.addAll(entry.getValue());
        }

        // 3.b check for upstream effects
        Behaviour upstream = getUpstream(cause, directDownstreams);

        if (upstream != null) {
            return Pair.of(upstream, transativeDownstreams);
        }

        // assume the happy path behaviour
        return Pair.of(causeBehaviour, transativeDownstreams);
    }
}
