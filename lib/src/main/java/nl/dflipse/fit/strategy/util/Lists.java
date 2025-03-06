package nl.dflipse.fit.strategy.util;

import java.util.List;
import java.util.stream.Collectors;

public class Lists {
    public static <T> List<T> minus(List<T> A, T e) {
        return A.stream()
                .filter(el -> !el.equals(e))
                .collect(Collectors.toList());
    }
}
