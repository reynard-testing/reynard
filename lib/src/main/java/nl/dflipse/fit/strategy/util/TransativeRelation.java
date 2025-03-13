package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TransativeRelation<X> {
    private final Map<X, X> inverseRelation = new HashMap<>();
    private final Map<X, Set<X>> relation = new HashMap<>();
    private final Map<X, Set<X>> transitiveRelations = new HashMap<>();

    public void addRelation(X parent, X child) {
        if ((parent == null && child == null) || (parent != null && parent.equals(child))) {
            throw new IllegalArgumentException(
                    "Cannot relate two equal items, this will create a circular dependency.");
        }

        if (hasTransativeRelation(child, parent)) {
            throw new IllegalArgumentException("Adding this relation would create a circular dependency.");
        }

        inverseRelation.put(child, parent);
        relation.computeIfAbsent(parent, k -> new HashSet<>()).add(child);
        transitiveRelations.computeIfAbsent(parent, k -> new HashSet<>()).add(child);
        updateTransitiveRelations(parent, child);
    }

    private void updateTransitiveRelations(X parent, X child) {
        // The children of the child are also transative children of the parent
        Set<X> children = getDecendants(child);
        for (X descendant : children) {
            transitiveRelations.computeIfAbsent(parent, k -> new HashSet<>()).add(descendant);
        }

        // The parent is a transative parent of the children
        var parentParent = inverseRelation.get(parent);
        if (parentParent != null) {
            updateTransitiveRelations(parentParent, parent);
        }
    }

    public boolean hasDirectRelation(X parent, X child) {
        return relation.containsKey(parent) && relation.get(parent).contains(child);
    }

    public boolean hasTransativeRelation(X parent, X child) {
        return transitiveRelations.containsKey(parent) && transitiveRelations.get(parent).contains(child);
    }

    public Set<X> getDecendants(X parent) {
        Set<X> descendants = new HashSet<>();

        if (relation.containsKey(parent)) {
            for (X child : relation.get(parent)) {
                descendants.add(child);
                descendants.addAll(getDecendants(child));
            }
        }
        return descendants;
    }

    public X getParent(X child) {
        return inverseRelation.get(child);
    }

    public List<Pair<X, X>> getRelations() {
        List<Pair<X, X>> relations = new ArrayList<>();
        for (X parent : relation.keySet()) {
            for (X child : relation.get(parent)) {
                relations.add(new Pair<>(parent, child));
            }
        }
        return relations;
    }

    public List<Pair<X, X>> getTransativeRelations() {
        List<Pair<X, X>> relations = new ArrayList<>();
        for (X parent : transitiveRelations.keySet()) {
            for (X child : transitiveRelations.get(parent)) {
                relations.add(new Pair<>(parent, child));
            }
        }
        return relations;
    }

    public List<X> getParents(X child) {
        List<X> parents = new ArrayList<>();
        X parent = getParent(child);
        while (parent != null) {
            parents.add(parent);
            parent = getParent(parent);
        }

        return parents;
    }

    public X getFirstCommonAncestor(X child1, X child2) {
        List<X> parents1 = getParents(child1);
        List<X> parents2 = getParents(child2);
        parents1.retainAll(parents2);
        return parents1.stream().findFirst().orElse(null);
    }

    public Set<X> getChildren(X parent) {
        return relation.getOrDefault(parent, Set.of());
    }

}
