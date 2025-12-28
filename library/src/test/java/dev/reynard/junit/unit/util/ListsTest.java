package dev.reynard.junit.unit.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.reynard.junit.strategy.util.Lists;

public class ListsTest {
    @Test
    public void testInsertAfter() {
        List<Integer> list1 = new ArrayList<Integer>(List.of(1, 2, 4, 5));
        int index1 = Lists.addAfter(list1, 3, i -> i == 2);
        assertEquals(List.of(1, 2, 3, 4, 5), list1);
        assertEquals(2, index1);
    }

    @Test
    public void testInsertBefore() {
        List<Integer> list1 = new ArrayList<Integer>(List.of(1, 2, 4, 5));
        int index1 = Lists.addBefore(list1, 3, i -> i == 4);
        assertEquals(List.of(1, 2, 3, 4, 5), list1);
        assertEquals(2, index1);

    }

    @Test
    public void testInsertBefore2() {
        List<Integer> list1 = new ArrayList<Integer>(List.of(1, 1, 2, 2));
        int toAdd = 1;
        int index1 = Lists.addBefore(list1, toAdd, i -> i > toAdd);
        assertEquals(List.of(1, 1, 1, 2, 2), list1);
        assertEquals(2, index1);
    }

    @Test
    public void testInsertAfter2() {
        List<Integer> list1 = new ArrayList<Integer>(List.of(1, 1, 2, 2));
        int toAdd = 1;
        int index1 = Lists.addAfter(list1, toAdd, i -> i > toAdd);
        assertEquals(List.of(1, 1, 2, 1, 2), list1);
        assertEquals(3, index1);
    }

    @Test
    public void testBinarySearch1() {
        List<Integer> list1 = new ArrayList<Integer>(List.of(1, 2, 3, 4, 5));
        int toFind = 4;
        int index = Lists.findIndexBinarySearch(list1, i -> i, toFind);
        assertEquals(3, index);
    }

    @Test
    public void testBinarySearch2() {
        List<Integer> list1 = new ArrayList<Integer>(List.of(1, 1, 2, 3, 4, 4, 4, 4, 5));
        int toFind = 4;
        int index = Lists.findIndexBinarySearch(list1, i -> i, toFind);
        assertTrue(index >= 4 && index <= 7);
    }
}
