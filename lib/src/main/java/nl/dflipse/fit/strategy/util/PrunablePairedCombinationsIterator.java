package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PrunablePairedCombinationsIterator<X, Y> implements Iterator<List<Pair<X, Y>>> {

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

    public PrunablePairedCombinationsIterator(List<X> xs, Set<Y> ys) {
        this(xs, List.copyOf(ys));
    }

    public PrunablePairedCombinationsIterator(List<X> xs, List<Y> ys) {
        this.xs = xs;
        this.ys = List.copyOf(ys);

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
        // If we are done, return false
        if (index < 0) {
            done = true;
            return false;
        }

        // can we increment this index?
        // then do so
        if (indices[index] < maxIndex) {
            indices[index]++;

            // if we should skip this combination, increment again
            int skipIndex = getSkipIndex();
            if (skipIndex == index) {
                return increment(index);
            }

            return true;
        }

        // If our left neighbour can be incremented, do so
        // and reset this index
        if (increment(index - 1)) {
            indices[index] = 0;
            return true;
        }

        // otherwise, we are done, forward this
        return false;

    }

    private int getSkipIndex(Map<Integer, Integer> subsetMap) {
        boolean matchAll = true;
        for (var entry : subsetMap.entrySet()) {
            if (indices[entry.getKey()] != entry.getValue()) {
                matchAll = false;
                break;
            }
        }

        if (matchAll) {
            return subsetMap.entrySet().stream()
                    .mapToInt(e -> e.getKey())
                    .max()
                    .getAsInt();
        }

        return -1;
    }

    private int getSkipIndex() {
        if (done) {
            return -1;
        }

        for (var subsetMap : pruned) {
            int skipIndex = getSkipIndex(subsetMap);
            if (skipIndex >= 0) {
                return skipIndex;
            }
        }

        return -1;
    }

    public void ensureNextValid() {
        int skipIndex = getSkipIndex();
        while (skipIndex >= 0 && !done) {
            increment(skipIndex);
            skipIndex = getSkipIndex();
        }
    }

    private void increment() {
        increment(lastIndex);
        ensureNextValid();
    }

    private String readableIndex() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indices.length; i++) {
            sb.append("" + indices[i]);
        }
        return sb.toString();
    }

    @Override
    public List<Pair<X, Y>> next() {
        var res = currentCombination();
        // String readableIndex = readableIndex();
        increment();
        return res;
    }

    // a <= B?
    public boolean isSubsetOf(Map<Integer, Integer> A, Map<Integer, Integer> B) {
        for (var key : A.keySet()) {
            if (!B.containsKey(key)) {
                return false;
            }

            if (A.get(key) != B.get(key)) {
                return false;
            }
        }

        return true;
    }

    public void prune(Set<Pair<X, Y>> toProne) {
        Map<Integer, Integer> newPruned = new HashMap<>();

        for (var pair : toProne) {
            int XIndex = xs.indexOf(pair.first());
            int YIndex = ys.indexOf(pair.second());

            if (XIndex == -1 || YIndex == -1) {
                // This pair is not in the list
                return;
            }

            newPruned.put(XIndex, YIndex);
        }

        var iterator = pruned.iterator();
        while (iterator.hasNext()) {
            var alreadyPruned = iterator.next();
            // Existing subset of newPruned?
            if (isSubsetOf(alreadyPruned, newPruned)) {
                // This subset is already pruned
                return;
            }

            // New subset of alreadyPruned?
            if (isSubsetOf(newPruned, alreadyPruned)) {
                // This subset is a superset of an already pruned subset
                // so we should remove the already pruned subset
                iterator.remove();
            }
        }

        pruned.add(newPruned);

        int skipIndex = getSkipIndex(newPruned);
        if (skipIndex >= 0) {
            increment(skipIndex);
        }
    }

}
