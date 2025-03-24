package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MinimalSubsetTrie<E> {
    private Map<E, MinimalSubsetTrie<E>> children = new HashMap<>();
    private boolean isRoot;

    private List<E> consistentOrder(Set<E> set) {
        List<E> list = new ArrayList<>(set);
        Collections.sort(list, Comparator.comparing(Object::hashCode));
        return list;
    }

    public MinimalSubsetTrie() {
        this.isRoot = true;
    }

    public boolean isLeaf() {
        return !isRoot && children.isEmpty();
    }

    // --- Add subset ---
    public void add(Set<E> subset) {
        if (subset.isEmpty()) {
            return;
        }

        add(consistentOrder(subset));
    }

    private void add(List<E> orderedSubset) {
        if (orderedSubset.isEmpty()) {
            return;
        }

        E head = orderedSubset.get(0);
        List<E> tail = orderedSubset.subList(1, orderedSubset.size());

        if (children.containsKey(head)) {
            var node = children.get(head);
            if (tail.isEmpty()) {
                if (!node.isLeaf()) {
                    node.children.clear();
                }
            } else {
                node.add(tail);
            }
        } else {
            MinimalSubsetTrie<E> child = new MinimalSubsetTrie<>();
            child.isRoot = false;
            child.add(tail);
            children.put(head, child);
        }
    }

    // --- Has Subset ---
    public boolean hasSubset(Set<E> superset) {
        if (superset.isEmpty()) {
            return false;
        }

        return hasSubset(consistentOrder(superset));
    }

    private boolean hasSubset(List<E> superset) {
        return hasSubsetHelper(this, superset, 0);
    }

    private boolean hasSubsetHelper(MinimalSubsetTrie<E> node, List<E> superset, int index) {
        if (node.isLeaf()) {
            return true;
        }

        for (int i = index; i < superset.size(); i++) {
            E head = superset.get(i);

            if (!node.children.containsKey(head)) {
                continue;
            }

            if (hasSubsetHelper(node.children.get(head), superset, i + 1)) {
                return true;
            }
        }

        return false;
    }

    public Set<Set<E>> getAll() {
        if (isLeaf()) {
            return Set.of();
        }

        Set<Set<E>> all = new HashSet<>();
        for (var entry : children.entrySet()) {
            E element = entry.getKey();
            MinimalSubsetTrie<E> child = entry.getValue();

            if (child.isLeaf()) {
                all.add(Set.of(element));
                continue;
            }

            for (var childSet : child.getAll()) {
                Set<E> newSet = Sets.plus(childSet, element);
                all.add(newSet);
            }
        }

        return all;
    }
}
