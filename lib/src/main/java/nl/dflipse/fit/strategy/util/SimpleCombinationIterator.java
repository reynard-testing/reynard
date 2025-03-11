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
public class SimpleCombinationIterator<T> implements Iterator<List<T>> {

    private final List<T> originalVector;
    private final int takeN;
    private final List<T> currentCombination = new ArrayList<>();
    // Internal array
    private final int[] bitVector;
    private long currentIndex;
    // Criteria to stop iterating the combinations.
    private int endIndex = 0;

    public SimpleCombinationIterator(List<T> originalVector, int takeN) {
        this.originalVector = new ArrayList<>(originalVector);
        this.takeN = takeN;

        this.bitVector = new int[takeN + 1];
        for (int i = 0; i <= takeN; i++) {
            this.bitVector[i] = i;
        }
        if (originalVector.size() > 0) {
            this.endIndex = 1;
        }
        this.currentIndex = 0;
    }

    private void setValue(int index, T value) {
        if (index < this.currentCombination.size()) {
            this.currentCombination.set(index, value);
        } else {
            this.currentCombination.add(index, value);
        }
    }

    /**
     * Returns true if all combinations were iterated, otherwise false
     */
    @Override
    public boolean hasNext() {
        return !((this.endIndex == 0) || (this.takeN > this.originalVector.size()));
    }

    /**
     * Moves to the next combination
     */
    @Override
    public List<T> next() {
        this.currentIndex++;

        for (int i = 1; i <= this.takeN; i++) {
            int index = this.bitVector[i] - 1;
            if (this.originalVector.size() > 0) {
                this.setValue(i - 1, this.originalVector.get(index));
            }
        }

        this.endIndex = this.takeN;
        while (this.bitVector[this.endIndex] == this.originalVector.size() - this.takeN
                + endIndex) {
            this.endIndex--;
            if (endIndex == 0) {
                break;
            }
        }
        this.bitVector[this.endIndex]++;
        for (int i = this.endIndex + 1; i <= this.takeN; i++) {
            this.bitVector[i] = this.bitVector[i - 1] + 1;
        }

        // Return a copy of the current combination.
        return new ArrayList<>(this.currentCombination);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "SimpleCombinationIterator=[#" + this.currentIndex + ", " + this.currentCombination
                + "]";
    }
}