package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Lists {
    public static <T> List<T> minus(List<T> A, T e) {
        return A.stream()
                .filter(el -> !el.equals(e))
                .collect(Collectors.toList());
    }

    public static <T> List<T> add(List<T> A, T e) {
        var list = new ArrayList<>(A);
        list.add(e);
        return list;
    }
}
