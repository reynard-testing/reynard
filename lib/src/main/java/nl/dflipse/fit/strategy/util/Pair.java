package nl.dflipse.fit.strategy.util;

public record Pair<T1, T2>(T1 first, T2 second) {
    public T1 getFirst() {
        return first;
    }

    public T2 getSecond() {
        return second;
    }

}
