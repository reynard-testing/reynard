package nl.dflipse.fit.strategy.util;

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

        // P(x :: S) = S ∪ { x ∪ Ps | Ps ∈ S }
        for (List<T> set : generatePowerSet(rest)) {
            // Add the set itself
            powerSet.add(set);

            // add the head to the set, and add that set.
            List<T> newSet = new ArrayList<>();
            newSet.add(head);
            newSet.addAll(set);
            powerSet.add(newSet);
        }

        return powerSet;
    }

    public static <T> List<List<T>> combinations(List<T> originalSet, int k) {
        List<List<T>> res = new ArrayList<>();
        var it = new SimpleCombinationIterator<>(originalSet, k);
        while (it.hasNext()) {
            res.add(it.next());
        }
        return res;
    }

    // Generate all unique lists of pairs of elements from xs and ys
    // where xs is always present
    public static <X, Y> List<List<Pair<X, Y>>> cartesianCombinations(List<X> xs, List<Y> ys) {
        List<List<Pair<X, Y>>> res = List.of(List.of());

        if (xs.isEmpty() || ys.isEmpty()) {
            return res;
        }

        for (var x : xs) {
            List<List<Pair<X, Y>>> newRes = new ArrayList<>();

            for (var l : res) {
                for (var y : ys) {
                    // create a new list with the new pair
                    Pair<X, Y> pair = new Pair<>(x, y);
                    List<Pair<X, Y>> newList = new ArrayList<>(l);
                    newList.add(pair);
                    newRes.add(newList);
                }
            }

            res = newRes;
        }

        return res;
    }

}