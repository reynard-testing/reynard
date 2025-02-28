package nl.dflipse.fit.strategy.util;

/*
 * Adapted from Combinatorics Library 3
 * Copyright 2009-2016 Dmytro Paukov d.paukov@gmail.com
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Iterator for the simple combination generator.
 *
 * @param <T> Type of the elements in the combinations.
 * @author Dmytro Paukov
 * @version 3.0
 * @see SimpleCombinationGenerator
 */
public class AllCombinationIterator<T> implements Iterator<List<T>> {
    private final List<T> originalVector;
    private int takeN = 1;
    private final int maxCombinations;
    private SimpleCombinationIterator<T> it;

    public AllCombinationIterator(List<T> originalVector) {
        this.originalVector = originalVector;
        this.maxCombinations = (int) Math.pow(2, originalVector.size()) - 1;
        this.it = new SimpleCombinationIterator<>(originalVector, takeN);
    }

    public int size() {
        return maxCombinations;
    }

    private boolean hasMoreSizes() {
        return takeN < originalVector.size();
    }

    /**
     * Returns true if all combinations were iterated, otherwise false
     */
    @Override
    public boolean hasNext() {
        return it.hasNext() || hasMoreSizes();
    }

    /**
     * Moves to the next combination
     */
    @Override
    public List<T> next() {
        if (!it.hasNext() && hasMoreSizes()) {
            takeN++;
            it = new SimpleCombinationIterator<>(originalVector, takeN);
        }

        return it.next();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "AllCombinationIterator=[#" + this.takeN + ", " + this.maxCombinations
                + "]";
    }
}