package nl.dflipse.fit.strategy.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

public class SubsetStore<E> {
    private final List<Set<E>> sets = new ArrayList<>();
    private final boolean allowNull = false;
    private final boolean minimize;

    public SubsetStore(boolean minimize) {
        this.minimize = minimize;
    }

    public SubsetStore() {
        this(true);
    }

    private BiPredicate<E, E> equality = (a, b) -> a.equals(b);

    public SubsetStore<E> withEquality(BiPredicate<E, E> equality) {
        this.equality = equality;
        return this;
    }

    // if a <= b
    private boolean isSubsetOf(Set<E> a, Set<E> b) {
        for (E e : a) {
            boolean found = false;

            for (E f : b) {
                if (equality.test(e, f)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }

    public List<Set<E>> getSets() {
        return sets;
    }

    public int getMaximalSubsetSize() {
        return sets.stream()
                .mapToInt(Set::size)
                .max()
                .orElse(0);
    }

    public int getMinimalSubsetSize() {
        return sets.stream()
                .mapToInt(Set::size)
                .min()
                .orElse(0);
    }

    public boolean hasSubsetOf(Set<E> set) {
        if (set.isEmpty() && !allowNull) {
            return false;
        }

        for (Set<E> s : sets) {
            // if s <= set
            if (isSubsetOf(s, set)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasSupersetOf(Set<E> set) {
        if (set.isEmpty() && !sets.isEmpty()) {
            return true;
        }

        for (Set<E> s : sets) {
            // if set <= s
            if (isSubsetOf(set, s)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasSet(Set<E> set) {
        if (set.isEmpty() && !allowNull) {
            return false;
        }

        for (Set<E> s : sets) {
            if (s.equals(set)) {
                return true;
            }
        }

        return false;
    }

    public void add(Set<E> set) {
        if (set.isEmpty() && !allowNull) {
            return;
        }

        if (minimize) {
            if (hasSubsetOf(set)) {
                return;
            }

            // remove all sets that are subsets of the new set
            // if set <= s
            sets.removeIf(s -> isSubsetOf(set, s));
        }

        sets.add(set);
    }

    public int size() {
        return sets.size();
    }

}
