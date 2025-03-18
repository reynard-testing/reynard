package nl.dflipse.fit.generators;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;

import nl.dflipse.fit.strategy.util.SubsetSearchTree;

public class SubsetSearchTreeTest {
    @Test
    public void testNothing() {
        SubsetSearchTree<Integer> tree = new SubsetSearchTree<>();
        assertFalse(tree.containsSubset(Set.of(1)));
    }

    @Test
    public void testOne() {
        SubsetSearchTree<Integer> tree = new SubsetSearchTree<>();
        Set<Integer> subset = Set.of(1);
        tree.addSubset(subset);
        assertTrue(tree.containsSubset(subset));
    }

    @Test
    public void testMany() {
        SubsetSearchTree<Integer> tree = new SubsetSearchTree<>();
        Set<Integer> subset = Set.of(1, 2, 3);
        tree.addSubset(subset);
        assertTrue(tree.containsSubset(subset));
    }

    @Test
    public void testSmallBig() {
        SubsetSearchTree<Integer> tree = new SubsetSearchTree<>();
        Set<Integer> subset = Set.of(1, 2, 3);
        Set<Integer> testSubset = Set.of(1);
        tree.addSubset(subset);
        assertFalse(tree.containsSubset(testSubset));
    }

    @Test
    public void testBigSmall() {
        SubsetSearchTree<Integer> tree = new SubsetSearchTree<>();
        Set<Integer> subset = Set.of(1);
        Set<Integer> testSubset = Set.of(1, 2, 3);
        tree.addSubset(subset);
        assertTrue(tree.containsSubset(testSubset));
    }

    @Test
    public void testSupersetAddition1() {
        SubsetSearchTree<Integer> tree = new SubsetSearchTree<>();
        tree.addSubset(Set.of(1, 2, 3, 4));
        tree.addSubset(Set.of(1, 2));
        assertTrue(tree.containsSubset(Set.of(1, 2)));
    }

    @Test
    public void testSupersetAddition2() {
        SubsetSearchTree<Integer> tree = new SubsetSearchTree<>();
        tree.addSubset(Set.of(1, 2));
        tree.addSubset(Set.of(1, 2, 3, 4));
        assertTrue(tree.containsSubset(Set.of(1, 2)));
    }

    @Test
    public void testSupersetAddition3() {
        SubsetSearchTree<Integer> tree = new SubsetSearchTree<>();
        tree.addSubset(Set.of(1, 2, 4));
        tree.addSubset(Set.of(1, 2, 3));
        assertFalse(tree.containsSubset(Set.of(1, 2)));
    }
}
