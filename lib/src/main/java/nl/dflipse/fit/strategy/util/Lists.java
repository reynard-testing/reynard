package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Lists {
    public static <T> List<T> minus(Collection<T> A, T e) {
        return A.stream()
                .filter(el -> !el.equals(e))
                .collect(Collectors.toList());
    }

    public static <T> List<T> plus(List<T> A, T e) {
        var list = new ArrayList<>(A);
        list.add(e);
        return list;
    }

    /** Return A - B */
    public static <T> List<T> difference(Collection<T> A, Collection<T> B) {
        List<T> difference = new ArrayList<>(A);
        difference.removeAll(B);
        return difference;
    }

    public static <T> int addAfter(List<T> list, T element, Predicate<T> predicate) {
        for (int i = 0; i < list.size(); i++) {
            if (predicate.test(list.get(i))) {
                list.add(i, element);
                return i;
            }
        }

        list.add(element);
        return -1;
    }
}
