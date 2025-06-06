package io.github.delanoflipse.fit.suite.strategy.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// TODO: seperate into relation and build transitive relation on top
public class TransativeRelation<X> {
    private final Set<X> elements = new LinkedHashSet<>();
    private final Map<X, Set<X>> inverseRelation = new LinkedHashMap<>();
    private final Map<X, Set<X>> relation = new LinkedHashMap<>();
    private final Map<X, Set<X>> transitiveRelations = new LinkedHashMap<>();

    public void setAddAll(Map<X, Set<X>> mapping, X key, Collection<X> elements) {
        mapping.computeIfAbsent(key, k -> new LinkedHashSet<>());
        Set<X> existing = mapping.get(key);
        existing.addAll(elements);
    }

    public void setAdd(Map<X, Set<X>> mapping, X key, X element) {
        setAddAll(mapping, key, List.of(element));
    }

    public void addRelation(X parent, X child) {
        elements.add(parent);
        elements.add(child);

        if ((parent == null && child == null) || (parent != null && parent.equals(child))) {
            throw new IllegalArgumentException(
                    "Cannot relate two equal items, this will create a circular dependency.");
        }

        if (hasTransativeRelation(child, parent)) {
            throw new IllegalArgumentException("Adding this relation would create a circular dependency.");
        }

        setAdd(relation, parent, child);
        setAdd(inverseRelation, child, parent);
        updateTransitiveRelations(parent, child);
    }

    private void updateTransitiveRelations(X parent, X child) {
        Set<X> descendants = getDecendantsOf(child);
        Set<X> descendantsAndChild = Sets.plus(descendants, child);
        Set<X> predecessors = getPredecessorsOf(child);

        // The children of the child
        // Are now too the children of the childs's parents' parents
        for (X predecessor : predecessors) {
            setAddAll(transitiveRelations, predecessor, descendantsAndChild);
        }
    }

    public boolean hasDirectRelation(X parent, X child) {
        return relation.containsKey(parent) && relation.get(parent).contains(child);
    }

    public boolean hasTransativeRelation(X parent, X child) {
        return transitiveRelations.containsKey(parent) && transitiveRelations.get(parent).contains(child);
    }

    public boolean areRelated(X item1, X item2) {
        return hasTransativeRelation(item1, item2) || hasTransativeRelation(item2, item1);
    }

    public Set<X> getChildren(X parent) {
        return relation.getOrDefault(parent, Set.of());
    }

    public Set<X> getDecendants(X parent) {
        return transitiveRelations.getOrDefault(parent, Set.of());
    }

    public Set<X> getParentsOf(X child) {
        return inverseRelation.getOrDefault(child, Set.of());
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

    private Set<X> getPredecessorsOf(X node) {
        Set<X> predecessors = new LinkedHashSet<>();
        Deque<X> toVisit = new ArrayDeque<>();

        toVisit.addAll(getParentsOf(node));

        while (!toVisit.isEmpty()) {
            X parent = toVisit.poll();
            if (predecessors.contains(parent)) {
                continue; // Already visited this parent
            }
            predecessors.add(parent);
            toVisit.addAll(getParentsOf(parent));
        }

        return predecessors;
    }

    private Set<X> getDecendantsOf(X node) {
        Set<X> predecessors = new LinkedHashSet<>();
        Deque<X> toVisit = new ArrayDeque<>();

        toVisit.addAll(getChildren(node));

        while (!toVisit.isEmpty()) {
            X parent = toVisit.poll();
            if (predecessors.contains(parent)) {
                continue; // Already visited this parent
            }
            predecessors.add(parent);
            toVisit.addAll(getChildren(parent));
        }

        return predecessors;
    }

    public Set<X> getElements() {
        return elements;
    }

    public List<X> topologicalOrder() {
        Map<X, Integer> inDegreeByNode = new HashMap<>();
        Map<X, List<X>> adjacentByNode = new HashMap<>();

        for (X parent : relation.keySet()) {
            for (X child : relation.get(parent)) {
                // Add edge
                adjacentByNode.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
                // Set degree
                int currentDegree = inDegreeByNode.getOrDefault(child, 0);
                inDegreeByNode.put(child, currentDegree + 1);
                // Ensure parent has a value in the degree map
                inDegreeByNode.putIfAbsent(parent, inDegreeByNode.getOrDefault(parent, 0));
            }
        }

        var inOrder = new ArrayList<X>();
        var queue = new ArrayDeque<X>();

        // Find roots
        for (X node : elements) {
            if (inDegreeByNode.getOrDefault(node, 0) == 0) {
                queue.add(node);
            }
        }

        while (!queue.isEmpty()) {
            X node = queue.poll();
            inOrder.add(node);

            for (X neighbor : adjacentByNode.getOrDefault(node, List.of())) {
                // Remove edge
                inDegreeByNode.put(neighbor, inDegreeByNode.get(neighbor) - 1);

                // If in-degree is now zero, add to queue
                if (inDegreeByNode.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        return inOrder;
    }

}
