package nl.dflipse.fit.strategy.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UndirectedRelation<X> {
    private final Map<X, Set<X>> relation = new HashMap<>();

    public void addRelation(X a, X b) {
        relation.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        relation.computeIfAbsent(b, k -> new HashSet<>()).add(a);
    }

    public boolean areRelated(X a, X b) {
        return relation.containsKey(a) && relation.get(a).contains(b);
    }

    public Set<X> getRelated(X a) {
        return relation.getOrDefault(a, new HashSet<>());
    }

}
