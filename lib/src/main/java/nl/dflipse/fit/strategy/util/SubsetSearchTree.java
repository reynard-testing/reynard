package nl.dflipse.fit.strategy.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SubsetSearchTree<E> {

    private final Map<E, Node<E>> subsetLookup = new HashMap<>();
    private int size = 0;

    public class Node<NE> {
        public final NE element;
        public final Map<NE, Node<NE>> children;
        private boolean isLeaf;

        public boolean isLeaf() {
            return isLeaf;
        }

        public Node(NE element, boolean isLeaf) {
            this.element = element;
            this.isLeaf = isLeaf;
            if (isLeaf) {
                this.children = null;
            } else {
                this.children = new HashMap<>();
            }
        }
    }

    public boolean addSubset(E element, Set<E> others, Map<E, Node<E>> map) {
        boolean isLeaf = others.isEmpty();
        Node<E> node = getNodeOrAdd(element, isLeaf, map);

        // We are a larger subset than before
        // We can ignore the new subset
        if (node.isLeaf()) {
            return false;
        }

        // We have a smaller subset than before
        // Remove the old subset
        if (isLeaf && !node.isLeaf()) {
            Node<E> leafNode = new Node<>(element, isLeaf);
            map.put(element, leafNode);
            return true;
        }

        boolean isNew = true;

        for (E other : others) {
            Set<E> subOthers = Sets.minus(others, other);
            isNew = isNew && addSubset(other, subOthers, node.children);
        }

        return isNew;
    }

    public boolean addSubset(Set<E> subset) {
        boolean isNew = true;
        for (E element : subset) {
            Set<E> others = Sets.minus(subset, element);
            isNew = isNew && addSubset(element, others, subsetLookup);
        }

        if (isNew) {
            size++;
        }

        return isNew;
    }

    public Node<E> getNodeOrAdd(E element, boolean isLeaf, Map<E, Node<E>> map) {
        if (!map.containsKey(element)) {
            Node<E> node = new Node<>(element, isLeaf);
            map.put(element, node);
            return node;
        }

        return map.get(element);
    }

    public boolean containsSubset(E el, Set<E> tail, Map<E, Node<E>> map) {
        Set<E> others = Sets.minus(tail, el);

        if (!map.containsKey(el)) {
            return false;
        }

        Node<E> node = map.get(el);
        if (node.isLeaf()) {
            return true;
        }

        if (others.isEmpty()) {
            return false;
        }

        return containsSubset(others, node.children);
    }

    public boolean containsSubset(Set<E> subset, Map<E, Node<E>> map) {
        for (E element : subset) {
            Set<E> others = Sets.minus(subset, element);
            if (containsSubset(element, others, map)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsSubset(Set<E> subset) {
        return containsSubset(subset, subsetLookup);
    }

    public int size() {
        return size;
    }

}
