package io.github.delanoflipse.fit.suite.strategy.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Lists {
    public static <T> List<T> minus(Collection<T> A, T e) {
        return A.stream()
                .filter(el -> !el.equals(e))
                .collect(Collectors.toList());
    }

    /** Return A ∪ B */
    public static <T> List<T> union(Collection<T> A, Collection<T> B) {
        List<T> union = new ArrayList<>(A);
        union.addAll(B);
        return union;
    }

    public static <T> List<T> plus(List<T> A, T e) {
        var list = new ArrayList<>(A);
        list.add(e);
        return list;
    }

    public static <T> List<T> plus(T e, List<T> A) {
        var list = new ArrayList<T>();
        list.add(e);
        list.addAll(A);
        return list;
    }

    /** Return A - B */
    public static <T> List<T> difference(Collection<T> A, Collection<T> B) {
        List<T> difference = new ArrayList<>(A);
        difference.removeAll(B);
        return difference;
    }

    public static <T> int addAfter(List<T> list, T element, Predicate<T> predicate) {
        for (int i = 0; i < list.size() - 1; i++) {
            if (predicate.test(list.get(i))) {
                int insertionIndex = i + 1;
                list.add(insertionIndex, element);
                return insertionIndex;
            }
        }

        list.add(element);
        return -1;
    }

    public static <T> int addBefore(List<T> list, T element, Predicate<T> predicate) {
        for (int i = 0; i < list.size(); i++) {
            if (predicate.test(list.get(i))) {
                list.add(i, element);
                return i;
            }
        }

        list.add(element);
        return -1;
    }

    public static <T> int findIndex(List<T> list, Predicate<T> predicate) {
        for (int i = 0; i < list.size(); i++) {
            if (predicate.test(list.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public static <T> int findIndexBinarySearch(List<T> list, Function<T, Integer> mapping, int value) {
        int low = 0;
        int high = list.size() - 1;

        while (low <= high) {
            int mid = (low + high) / 2;
            T midElement = list.get(mid);
            int elementValue = mapping.apply(midElement);
            if (elementValue < value) {
                low = mid + 1; // Search in the right half
            } else if (elementValue > value) {
                high = mid - 1; // Search in the left half
            } else {
                return mid; // Element found
            }
        }
        return -1; // Element not found
    }

    public static int findIndexBinarySearch(List<Integer> list, int value) {
        return findIndexBinarySearch(list, i -> i, value);
    }
}
