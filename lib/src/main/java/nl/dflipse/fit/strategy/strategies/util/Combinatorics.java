package nl.dflipse.fit.strategy.strategies.util;

import java.util.ArrayList;
import java.util.List;

public class Combinatorics {
    public static <T> List<List<T>> generatePowerSet(List<T> originalSet) {
        List<List<T>> powerSet = new ArrayList<>();

        if (originalSet.isEmpty()) {
            powerSet.add(new ArrayList<>());
            return powerSet;
        }

        List<T> list = new ArrayList<>(originalSet);
        T head = list.get(0);
        List<T> rest = list.subList(1, list.size());

        for (List<T> set : generatePowerSet(rest)) {
            List<T> newSet = new ArrayList<>();
            newSet.add(head);
            newSet.addAll(set);
            powerSet.add(newSet);
            powerSet.add(set);
        }

        powerSet.sort((a, b) -> Integer.compare(a.size(), b.size()));
        return powerSet;
    }
}