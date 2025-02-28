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
public class PowersetIterator<T> implements Iterator<List<T>> {

    private final List<T> originalVector;

    // Internal array
    private long index;
    private long lastIndex;

    public PowersetIterator(List<T> originalVector, boolean includeEmptySet) {
        this.originalVector = new ArrayList<>(originalVector);
        float combinations = (float) Math.pow(2, originalVector.size());

        if (combinations > Long.MAX_VALUE) {
            throw new IllegalArgumentException("The number of combinations is too large to be efficiently handled");
        }

        this.lastIndex = (long) Math.pow(2, originalVector.size()) - 1;
        this.index = includeEmptySet ? 0 : 1;
    }

    /**
     * Returns true if all combinations were iterated, otherwise false
     */
    @Override
    public boolean hasNext() {
        return this.index <= this.lastIndex;
    }

    public long size() {
        return (long) (this.lastIndex + 1);
    }

    /**
     * Moves to the next combination
     */
    @Override
    public List<T> next() {
        List<T> currentCombination = new ArrayList<>();
        long index = this.index;

        for (int i = 0; i < this.originalVector.size(); i++) {
            if ((index & 1) == 1) {
                currentCombination.add(this.originalVector.get(i));
            }

            index >>= 1;
        }
        this.index++;
        return currentCombination;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "PowersetIterator=[#" + this.index + "/" + this.lastIndex + "]";
    }
}