package dev.reynard.junit.strategy.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class LookupList<X, Y> {
    private final List<Y> list;
    private final Map<X, List<Y>> lookup;
    Function<Y, X> keyExtractor;

    public LookupList(Function<Y, X> keyExtractor) {
        this.list = new ArrayList<>();
        this.lookup = new LinkedHashMap<>();
        this.keyExtractor = keyExtractor;
    }

    public void add(Y value) {
        list.add(value);
        X key = keyExtractor.apply(value);
        lookup.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(value);
    }

    public List<Y> get(X key) {
        return lookup.getOrDefault(key, java.util.Collections.emptyList());
    }

    public List<Y> getByValue(Y related) {
        X key = keyExtractor.apply(related);
        return lookup.getOrDefault(key, java.util.Collections.emptyList());
    }

    public void removeIf(Predicate<Y> predicate) {
        list.removeIf(predicate);
        lookup.forEach((key, values) -> values.removeIf(predicate));
    }

    public List<Y> getAll() {
        return list;
    }
}
