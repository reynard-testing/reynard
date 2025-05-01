package io.github.delanoflipse.fit.suite.strategy.util;

public record Pair<T1, T2>(T1 first, T2 second) {

    public static <T1, T2> Pair<T1, T2> of(T1 first, T2 second) {
        return new Pair<>(first, second);
    }

}
