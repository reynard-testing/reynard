package io.github.delanoflipse.fit.suite.strategy.util.traversal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.delanoflipse.fit.suite.strategy.util.Pair;

public class TraversalStrategy<X> {
    private final TraversalOrder mode;

    public TraversalStrategy(TraversalOrder mode) {
        this.mode = mode;
    }

    public List<X> traverse(X root, List<Pair<X, X>> edges) {
        // Ensure that we only visit each node once
        // In case of cycles, this will prevent infinite loops
        Set<X> visited = new LinkedHashSet<>();
        visited.add(root);

        switch (mode) {
            case DEPTH_FIRST_PRE_ORDER,
                    DEPTH_FIRST_REVERSE_PRE_ORDER,
                    DEPTH_FIRST_POST_ORDER,
                    DEPTH_FIRST_REVERSE_POST_ORDER -> {
                return visitNodeDfs(root, edges, visited);
            }

            case BREADTH_FIRST -> {
                return visitNodeBfs(root, edges, visited);
            }

            case RANDOM -> {
                Set<X> nodes = new LinkedHashSet<>();
                for (Pair<X, X> edge : edges) {
                    nodes.add(edge.first());
                    nodes.add(edge.second());
                }

                List<X> shuffled = new ArrayList<>(nodes);
                Collections.shuffle(shuffled);
                return shuffled;
            }

            default -> {
                throw new IllegalArgumentException("Unknown traversal order: " + mode);
            }
        }
    }

    private List<X> getChildren(X node, List<Pair<X, X>> edges, Set<X> visited) {
        List<X> children = new ArrayList<>();

        for (Pair<X, X> edge : edges) {
            if (edge.first().equals(node)) {
                var child = edge.second();
                if (!visited.contains(child)) {
                    visited.add(child);
                    children.add(child);
                }
            }
        }

        return children;
    }

    private List<X> visitNodeBfs(X node, List<Pair<X, X>> edges, Set<X> visited) {
        List<X> result = new ArrayList<>();

        result.add(node);
        for (X child : getChildren(node, edges, visited)) {
            result.addAll(visitNodeBfs(child, edges, visited));
        }

        return result;
    }

    private List<X> visitNodeDfs(X node, List<Pair<X, X>> edges, Set<X> visited) {
        List<X> children = getChildren(node, edges, visited);

        switch (mode.getNodeOrder()) {
            case PRE_ORDER -> {
                List<X> result = new ArrayList<>();
                result.add(node);
                for (X child : children) {
                    result.addAll(visitNodeDfs(child, edges, visited));
                }
                return result;
            }
            case POST_ORDER -> {
                List<X> postOrderResult = new ArrayList<>();
                for (X child : children) {
                    postOrderResult.addAll(visitNodeDfs(child, edges, visited));
                }
                postOrderResult.add(node);
                return postOrderResult;
            }

            case REVERSE_PRE_ORDER -> {
                List<X> reversePreOrderResult = new ArrayList<>();
                reversePreOrderResult.add(node);

                for (int i = children.size() - 1; i >= 0; i--) {
                    reversePreOrderResult.addAll(visitNodeDfs(children.get(i), edges, visited));
                }

                return reversePreOrderResult;
            }

            case REVERSE_POST_ORDER -> {
                List<X> reversePostOrderResult = new ArrayList<>();

                for (int i = children.size() - 1; i >= 0; i--) {
                    reversePostOrderResult.addAll(visitNodeDfs(children.get(i), edges, visited));
                }

                reversePostOrderResult.add(node);
                return reversePostOrderResult;
            }

            default -> throw new IllegalArgumentException("Unknown node order: " + mode.getNodeOrder());
        }

    }
}
