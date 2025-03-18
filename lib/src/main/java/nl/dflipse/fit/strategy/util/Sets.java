package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class Sets {
    public static <T> boolean isSubset(Set<T> parent, Set<T> child) {
        return parent.containsAll(child);
    }

    public static <T> boolean isSuperset(Set<T> set1, Set<T> set2) {
        return set2.containsAll(set1);
    }

    public static <T> boolean areEqual(Set<T> set1, Set<T> set2) {
        return set1.containsAll(set2) && set2.containsAll(set1);
    }

    /** Return A ∪ B */
    public static <T> Set<T> union(Set<T> A, Set<T> B) {
        Set<T> union = new java.util.HashSet<>(A);
        union.addAll(B);
        return union;
    }

    /** Return A ∩ B */
    public static <T> Set<T> intersection(Set<T> A, Set<T> B) {
        Set<T> intersection = new java.util.HashSet<>(A);
        intersection.retainAll(B);
        return intersection;
    }

    /** Return A - B */
    public static <T> Set<T> difference(Set<T> A, Set<T> B) {
        Set<T> difference = new java.util.HashSet<>(A);
        difference.removeAll(B);
        return difference;
    }

    /** Return A / B */
    public static <T> Set<T> minus(Set<T> A, T x) {
        Set<T> difference = new java.util.HashSet<>(A);
        difference.remove(x);
        return difference;
    }

    /** Return A + B */
    public static <T> Set<T> plus(Set<T> A, T x) {
        Set<T> union = new java.util.HashSet<>(A);
        union.add(x);
        return union;
    }

    /**
     * Return all pairs of elements in A
     * 
     * @param <T> Type of elements in the set
     * @param A   set of elements
     * @return List of pairs of elements in A
     */
    public static <T> List<Pair<T, T>> pairs(Set<T> A) {
        List<Pair<T, T>> pairs = new ArrayList<>();
        for (T a : A) {
            for (T b : A) {
                if (a.equals(b)) {
                    continue;
                }

                pairs.add(new Pair<>(a, b));
            }
        }
        return pairs;
    }

    public static <T> boolean anyPair(Set<T> A, Predicate<Pair<T, T>> predicate) {
        var pairs = pairs(A);
        for (var pair : pairs) {
            if (predicate.test(pair)) {
                return true;
            }
        }
        return false;
    }

    public static <T> T getOnlyElement(Set<T> A) {
        if (A.size() == 1) {
            return A.iterator().next();
        }

        throw new IllegalArgumentException("Set does not contain exactly one element");
    }

    public static <T> T first(Set<T> A) {
        if (A.size() > 0) {
            return A.iterator().next();
        }

        return null;
    }
}
