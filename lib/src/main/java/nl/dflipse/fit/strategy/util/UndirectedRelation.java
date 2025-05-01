package io.github.delanoflipse.fit.strategy.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class UndirectedRelation<X> {
    // the relation maps x R y to y \in relation.get(x), x \in relation.get(y)
    private final Map<X, Set<X>> relation = new HashMap<>();
    // pairs, all pairs such that a R b
    private final Set<Set<X>> pairs = new HashSet<>();

    public void addRelation(X a, X b) {
        if (areRelated(a, b)) {
            return;
        }
        relation.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        relation.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        var newPair = Set.of(a, b);
        pairs.add(newPair);
    }

    public boolean areRelated(X a, X b) {
        return relation.containsKey(a) && relation.get(a).contains(b);
    }

    public Set<X> getRelated(X a) {
        return relation.getOrDefault(a, new HashSet<>());
    }

    public Map<X, Set<X>> getRelations() {
        return Map.copyOf(relation);
    }

    private Pair<X, X> getPair(Set<X> pair) {
        if (pair.size() != 2) {
            throw new IllegalArgumentException("Pair must contain exactly two elements");
        }
        List<X> list = List.copyOf(pair);
        return new Pair<>(list.get(0), list.get(1));
    }

    public List<Pair<X, X>> getPairs() {
        return pairs.stream()
                .map(s -> getPair(s))
                .collect(Collectors.toList());
    }

    public boolean isClique(Set<X> clique) {
        if (clique.size() < 3) {
            return true;
        }

        List<Set<X>> relatedPairs = pairs.stream()
                .filter(p -> Sets.isSubsetOf(p, clique))
                .collect(Collectors.toList());

        boolean isClique = relatedPairs.size() == clique.size() * (clique.size() - 1) / 2;
        return isClique;
    }
}
