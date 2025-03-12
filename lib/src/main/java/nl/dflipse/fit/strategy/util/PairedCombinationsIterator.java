package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PairedCombinationsIterator<X, Y> implements Iterator<List<Pair<X, Y>>> {

    private final List<X> xs;
    private final List<Y> ys;

    // Maps subset of pruned values to the index of the x value
    // So {0 -> 1, 1 -> 2} prunes [1, 2, *]
    private List<Map<Integer, Integer>> pruned = new ArrayList<>();

    // Indices of the current combination
    // each ith entry is the index of the y value for the ith x value
    // [0, 0, 0] for [A, B, C]/[0,1,2] means [A0, B0, C0]
    private int[] indices;
    private final int maxIndex;
    private final int lastIndex;
    private final int size;

    private boolean done = false;

    public PairedCombinationsIterator(List<X> xs, List<Y> ys) {
        this.xs = xs;
        this.ys = ys;

        maxIndex = ys.size() - 1;
        lastIndex = xs.size() - 1;
        size = (int) Math.pow(xs.size(), ys.size());
        this.indices = new int[xs.size()];
    }

    public int size() {
        return size;
    }

    @Override
    public boolean hasNext() {
        return !done;
    }

    private List<Pair<X, Y>> currentCombination() {
        var res = new ArrayList<Pair<X, Y>>();
        for (int i = 0; i < indices.length; i++) {
            res.add(new Pair<>(xs.get(i), ys.get(indices[i])));
        }
        return res;
    }

    private boolean increment(int index) {
        if (index < 0) {
            return false;
        }

        if (indices[index] < maxIndex) {
            indices[index]++;
            return true;
        } else {
            if (increment(index - 1)) {
                indices[index] = 0;
                return true;
            } else {
                return false;
            }
        }
    }

    private void increment() {
        done = !increment(lastIndex);
    }

    private boolean shouldSkip(Map<Integer, Integer> subsetMap) {
        for (var entry : subsetMap.entrySet()) {
            if (indices[entry.getKey()] != entry.getValue()) {
                return false;
            }
        }

        return true;
    }

    private boolean shouldSkip() {
        if (done) {
            return false;
        }

        for (var subsetMap : pruned) {
            if (shouldSkip(subsetMap)) {
                return true;
            }
        }

        return false;
    }

    public void ensureNextValid() {
        while (shouldSkip()) {
            increment();
        }
    }

    @Override
    public List<Pair<X, Y>> next() {
        var res = currentCombination();
        increment();
        ensureNextValid();
        return res;
    }

    public void prune(Set<Pair<X, Y>> toProne) {
        Map<Integer, Integer> prunedMap = new HashMap<>();

        for (var pair : toProne) {
            int XIndex = xs.indexOf(pair.first());
            int YIndex = ys.indexOf(pair.second());

            if (XIndex == -1 || YIndex == -1) {
                // This pair is not in the list
                return;
            }
            prunedMap.put(XIndex, YIndex);
        }

        pruned.add(prunedMap);

        if (shouldSkip(prunedMap)) {
            increment();
            ensureNextValid();
        }
    }

}
