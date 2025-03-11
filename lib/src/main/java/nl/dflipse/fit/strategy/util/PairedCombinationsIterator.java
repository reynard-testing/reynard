package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PairedCombinationsIterator<X, Y> implements Iterator<List<Pair<X, Y>>> {

    private final List<X> xs;
    private final List<Y> ys;

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

    @Override
    public List<Pair<X, Y>> next() {
        var res = currentCombination();
        increment();
        return res;
    }

}
