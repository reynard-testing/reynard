package nl.dflipse.fit.strategy.generators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PowersetGenerator<T> {
    private final List<PowersetExpander<T>> toExpand = new ArrayList<>();
    private final List<Set<T>> pruned = new ArrayList<>();

    private record PowersetExpander<T>(Set<T> value, List<T> expansion) {
    }

    public PowersetGenerator(List<T> elements) {
        // this.elements = elements;
        if (elements != null) {
            toExpand.add(new PowersetExpander<>(Set.of(), elements));
        }
    }

    public void expand(PowersetExpander<T> node) {
        if (node.expansion.isEmpty()) {
            return;
        }

        for (int i = 0; i < node.expansion.size(); i++) {
            List<T> newExpansion = node.expansion.subList(i + 1, node.expansion.size());

            Set<T> newValue = new HashSet<>(node.value);
            newValue.add(node.expansion.get(i));

            var newNode = new PowersetExpander<>(newValue, newExpansion);

            // Check if the new value is supposed to be pruned
            var shouldPrune = false;
            for (Set<T> prunedSet : pruned) {
                if (newValue.containsAll(prunedSet)) {
                    shouldPrune = true;
                    break;
                }
            }
            if (shouldPrune) {
                continue;
            }
            toExpand.add(newNode);
        }
    }

    public boolean hasNext() {
        boolean hasMore = !toExpand.isEmpty();
        return hasMore;
    }

    public Set<T> next() {
        PowersetExpander<T> node = toExpand.remove(0);
        expand(node);
        var value = node.value;
        return value;
    }

    public void prune(Set<T> subset) {
        pruned.add(subset);

        // Remove pruned nodes
        toExpand.removeIf(expander -> {
            // Prune if node's value is a subset
            if (expander.value.containsAll(subset)) {
                return true;
            }

            return false;
        });

        // Ensure that we do not expand any redundant cases
        // TODO
        // toExpand.replaceAll(expander -> {
        // return expander;
        // });
    }
}
