package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class PrunableGenericPowersetTreeIterator<N, E> implements Iterator<Set<N>> {
    private final List<E> elements;
    private final List<TreeNode<N, E>> toExpand = new ArrayList<>();
    private final List<Set<N>> prunedSubsets = new ArrayList<>();
    private final Function<E, Set<N>> expandMapper;

    public record TreeNode<N, E>(Set<N> value, List<E> expansion) {
    }

    public PrunableGenericPowersetTreeIterator(List<E> elements, Function<E, Set<N>> extensionMapper,
            boolean skipEmptySet) {
        this.expandMapper = extensionMapper;
        this.elements = new ArrayList<>();

        if (elements != null && !elements.isEmpty()) {
            this.elements.addAll(elements);
        }

        toExpand.add(new TreeNode<>(Set.of(), List.copyOf(elements)));

        if (skipEmptySet) {
            this.skip();
        }
    }

    private boolean shouldPrune(TreeNode<N, E> node, Set<N> prunedSubset) {
        return node.value.containsAll(prunedSubset);
    }

    private boolean shouldPrune(TreeNode<N, E> node) {
        for (Set<N> prunedSubset : prunedSubsets) {
            if (shouldPrune(node, prunedSubset)) {
                return true;
            }
        }

        return false;
    }

    public void expand(TreeNode<N, E> node) {
        if (node == null || node.expansion.isEmpty()) {
            return;
        }

        for (int i = 0; i < node.expansion.size(); i++) {
            E expansionElement = node.expansion.get(i);
            List<E> newExpansion = node.expansion.subList(i + 1, node.expansion.size());

            Set<N> expandsTo = expandMapper.apply(expansionElement);

            for (N additionalElement : expandsTo) {
                Set<N> newValue = Sets.plus(node.value(), additionalElement);
                var newNode = new TreeNode<>(newValue, newExpansion);

                // Check if the new value is supposed to be pruned
                if (shouldPrune(newNode)) {
                    continue;
                }

                toExpand.add(newNode);
            }

        }
    }

    @Override
    public boolean hasNext() {
        boolean hasMore = !toExpand.isEmpty();
        return hasMore;
    }

    public void skip() {
        expand(toExpand.remove(0));
    }

    @Override
    public Set<N> next() {
        TreeNode<N, E> node = toExpand.remove(0);
        expand(node);
        return node.value;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    // Add new element to explore
    public void add(E extension) {
        Set<N> expandsTo = expandMapper.apply(extension);

        for (N additionalElement : expandsTo) {
            Set<N> newValue = Set.of(additionalElement);
            var newNode = new TreeNode<>(newValue, List.copyOf(elements));

            if (shouldPrune(newNode)) {
                continue;
            }

            toExpand.add(newNode);
        }

        elements.add(extension);
    }

    public void prune(Set<N> subset) {
        prunedSubsets.add(subset);

        // Remove pruned nodes
        toExpand.removeIf(expander -> shouldPrune(expander, subset));
    }

    public long size(int m) {
        long sum = 0;
        for (var el : toExpand) {
            // TODO: update
            long contribution = (long) Math.pow(1 + m, el.expansion.size());
            sum += contribution;
        }

        return sum;
    }

    public long size() {
        return size(1);
    }
}
