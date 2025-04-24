package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class Sets {
    /** Whether A ⊆ B */
    public static <T> boolean isSubsetOf(Collection<T> A, Collection<T> B) {
        return B.containsAll(A);
    }

    /** Whether A ⊂ B */
    public static <T> boolean isProperSubset(Collection<T> A, Collection<T> B) {
        return B.stream().anyMatch(x -> !A.contains(x)) && isSubsetOf(A, B);
    }

    /** Whether A ⊇ B */
    public static <T> boolean isSupersetOf(Collection<T> A, Collection<T> B) {
        return A.containsAll(B);
    }

    /** Whether A ⊃ B */
    public static <T> boolean isProperSupersetOf(Collection<T> A, Collection<T> B) {
        return A.stream().anyMatch(x -> !B.contains(x)) && isSupersetOf(A, B);
    }

    public static <T> boolean areEqual(Collection<T> set1, Collection<T> set2) {
        return set1.containsAll(set2) && set2.containsAll(set1);
    }

    /** Return A ∪ B */
    public static <T> Set<T> union(Collection<T> A, Collection<T> B) {
        Set<T> union = new LinkedHashSet<>(A);
        union.addAll(B);
        return union;
    }

    /** Return A ∩ B */
    public static <T> Set<T> intersection(Collection<T> A, Collection<T> B) {
        Set<T> intersection = new LinkedHashSet<>(A);
        intersection.retainAll(B);
        return intersection;
    }

    /** Return A - B */
    public static <T> Set<T> difference(Collection<T> A, Collection<T> B) {
        Set<T> difference = new LinkedHashSet<>(A);
        difference.removeAll(B);
        return difference;
    }

    /** Return A / B */
    public static <T> Set<T> minus(Collection<T> A, T x) {
        Set<T> difference = new LinkedHashSet<>(A);
        difference.remove(x);
        return difference;
    }

    /** Return A + B */
    public static <T> Set<T> plus(Collection<T> A, T x) {
        Set<T> union = new LinkedHashSet<>(A);
        union.add(x);
        return union;
    }

    /** Return A - B + x */
    public static <T> Set<T> replace(Collection<T> A, Collection<T> B, T x) {
        return plus(difference(A, B), x);
    }

    /**
     * Return all pairs of elements in A
     * 
     * @param <T> Type of elements in the set
     * @param A   set of elements
     * @return List of pairs of elements in A
     */
    public static <T> List<Pair<T, T>> pairs(Collection<T> A) {
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

    public static <T> boolean anyPair(Collection<T> A, Predicate<Pair<T, T>> predicate) {
        var pairs = pairs(A);
        for (var pair : pairs) {
            if (predicate.test(pair)) {
                return true;
            }
        }
        return false;
    }

    public static <T> Set<T> replaceIf(Collection<T> A, Predicate<T> predicate, T replacement) {
        Set<T> result = new LinkedHashSet<>();
        for (T a : A) {
            if (predicate.test(a)) {
                result.add(replacement);
            } else {
                result.add(a);
            }
        }
        return result;
    }

    public static <T> T getOnlyElement(Collection<T> A) {
        if (A.size() == 1) {
            return A.iterator().next();
        }

        throw new IllegalArgumentException("Set does not contain exactly one element");
    }

    public static <T> T first(Collection<T> A) {
        if (A.size() > 0) {
            return A.iterator().next();
        }

        return null;
    }
}
