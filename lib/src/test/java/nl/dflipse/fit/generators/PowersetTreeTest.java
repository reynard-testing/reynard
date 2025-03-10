package nl.dflipse.fit.generators;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import nl.dflipse.fit.strategy.util.PrunablePowersetIterator;

public class PowersetTreeTest {

    private List<Set<Integer>> expandComplete(PrunablePowersetIterator<Integer> generator) {
        List<Set<Integer>> result = new ArrayList<>();
        while (generator.hasNext()) {
            result.add(generator.next());
        }
        return result;
    }

    private int expectedSize(int n) {
        return (int) Math.pow(2, n);
    }

    private void assertEqual(List<Set<Integer>> expected, List<Set<Integer>> actual) {
        assert expected.size() == actual.size();
        for (int i = 0; i < expected.size(); i++) {
            assert expected.get(i).equals(actual.get(i));
        }
    }

    @Test
    public void testNull() {
        PrunablePowersetIterator<Integer> generator = new PrunablePowersetIterator<>(null);
        assert generator.hasNext() == false;
        assertEqual(List.of(), expandComplete(generator));
    }

    @Test
    public void testEmpty() {
        PrunablePowersetIterator<Integer> generator = new PrunablePowersetIterator<>(new ArrayList<>());
        assertEqual(List.of(Set.of()), expandComplete(generator));
    }

    @Test
    public void testOne() {
        List<Integer> elements = List.of(1);
        PrunablePowersetIterator<Integer> generator = new PrunablePowersetIterator<>(elements);
        var expanded = expandComplete(generator);
        assert expanded.size() == expectedSize(elements.size());
        assertEqual(List.of(Set.of(), Set.of(1)), expanded);
    }

    @Test
    public void testTwo() {
        List<Integer> elements = List.of(1, 2);
        PrunablePowersetIterator<Integer> generator = new PrunablePowersetIterator<>(elements);
        var expanded = expandComplete(generator);
        assertEqual(List.of(
                Set.of(),
                Set.of(1),
                Set.of(2),
                Set.of(1, 2)),
                expanded);
    }

    @Test
    public void testThree() {
        List<Integer> elements = List.of(1, 2, 3);
        PrunablePowersetIterator<Integer> generator = new PrunablePowersetIterator<>(elements);
        var expanded = expandComplete(generator);
        assert expanded.size() == expectedSize(elements.size());
        assertEqual(List.of(
                Set.of(),
                Set.of(1),
                Set.of(2),
                Set.of(3),
                Set.of(1, 2),
                Set.of(1, 3),
                Set.of(2, 3),
                Set.of(1, 2, 3)),
                expanded);
    }

    @Test
    public void testFour() {
        List<Integer> elements = List.of(1, 2, 3, 4);
        PrunablePowersetIterator<Integer> generator = new PrunablePowersetIterator<>(elements);
        var expanded = expandComplete(generator);
        assert expanded.size() == expectedSize(elements.size());
        assertEqual(List.of(
                Set.of(),
                Set.of(1),
                Set.of(2),
                Set.of(3),
                Set.of(4),
                Set.of(1, 2),
                Set.of(1, 3),
                Set.of(1, 4),
                Set.of(2, 3),
                Set.of(2, 4),
                Set.of(3, 4),
                Set.of(1, 2, 3),
                Set.of(1, 2, 4),
                Set.of(1, 3, 4),
                Set.of(2, 3, 4),
                Set.of(1, 2, 3, 4)),
                expanded);
    }

    @Test
    public void testFourRemoveSubset() {
        List<Integer> elements = List.of(1, 2, 3, 4);
        PrunablePowersetIterator<Integer> generator = new PrunablePowersetIterator<>(elements);
        generator.prune(Set.of(2, 3));
        var expanded = expandComplete(generator);
        assertEqual(List.of(
                Set.of(),
                Set.of(1),
                Set.of(2),
                Set.of(3),
                Set.of(4),
                Set.of(1, 2),
                Set.of(1, 3),
                Set.of(1, 4),
                Set.of(2, 4),
                Set.of(3, 4),
                Set.of(1, 2, 4),
                Set.of(1, 3, 4)),
                expanded);
    }

    @Test
    public void testFourRemoveSubsetDuring1() {
        List<Integer> elements = List.of(1, 2, 3, 4);
        PrunablePowersetIterator<Integer> generator = new PrunablePowersetIterator<>(elements);
        assert Set.of().equals(generator.next());
        assert Set.of(1).equals(generator.next());
        assert Set.of(2).equals(generator.next());
        assert Set.of(3).equals(generator.next());
        assert Set.of(4).equals(generator.next());
        assert Set.of(1, 2).equals(generator.next());
        assert Set.of(1, 3).equals(generator.next());
        assert Set.of(1, 4).equals(generator.next());
        assert Set.of(2, 3).equals(generator.next());
        generator.prune(Set.of(2, 3));
        assert Set.of(2, 4).equals(generator.next());
        assert Set.of(3, 4).equals(generator.next());
        assert Set.of(1, 2, 4).equals(generator.next());
        assert Set.of(1, 3, 4).equals(generator.next());
        assert generator.hasNext() == false;
    }

    @Test
    public void testFourRemoveSubsetDuring2() {
        List<Integer> elements = List.of(1, 2, 3, 4);
        PrunablePowersetIterator<Integer> generator = new PrunablePowersetIterator<>(elements);
        assert Set.of().equals(generator.next());
        assert Set.of(1).equals(generator.next());
        assert Set.of(2).equals(generator.next());
        assert Set.of(3).equals(generator.next());
        assert Set.of(4).equals(generator.next());
        assert Set.of(1, 2).equals(generator.next());
        assert Set.of(1, 3).equals(generator.next());
        assert Set.of(1, 4).equals(generator.next());
        assert Set.of(2, 3).equals(generator.next());
        assert Set.of(2, 4).equals(generator.next());
        assert Set.of(3, 4).equals(generator.next());
        assert Set.of(1, 2, 3).equals(generator.next());
        generator.prune(Set.of(2, 3));
        assert Set.of(1, 2, 4).equals(generator.next());
        assert Set.of(1, 3, 4).equals(generator.next());
        // assert Set.of(2, 3, 4).equals(generator.next());
        // assert Set.of(1, 2, 3, 4).equals(generator.next());
        assert generator.hasNext() == false;

    }

    @Test
    public void testFourRemoveSubsetDuring3() {
        List<Integer> elements = List.of(1, 2, 3, 4);
        PrunablePowersetIterator<Integer> generator = new PrunablePowersetIterator<>(elements);
        assert Set.of().equals(generator.next());
        assert Set.of(1).equals(generator.next());
        assert Set.of(2).equals(generator.next());
        assert Set.of(3).equals(generator.next());
        assert Set.of(4).equals(generator.next());
        assert Set.of(1, 2).equals(generator.next());
        assert Set.of(1, 3).equals(generator.next());
        assert Set.of(1, 4).equals(generator.next());
        assert Set.of(2, 3).equals(generator.next());
        assert Set.of(2, 4).equals(generator.next());
        assert Set.of(3, 4).equals(generator.next());
        assert Set.of(1, 2, 3).equals(generator.next());
        assert Set.of(1, 2, 4).equals(generator.next());
        assert Set.of(1, 3, 4).equals(generator.next());
        assert Set.of(2, 3, 4).equals(generator.next());
        generator.prune(Set.of(2, 3));
        // assert Set.of(1, 2, 3, 4).equals(generator.next());
        assert generator.hasNext() == false;
    }

    @Test
    public void testFivePrunedSize() {
        List<Integer> elements = List.of(1, 2, 3, 4, 5);
        PrunablePowersetIterator<Integer> generator = new PrunablePowersetIterator<>(elements);
        generator.prune(Set.of(1, 2));
        int expectedCount = 32 - 8;

        int count = 0;
        while (generator.hasNext()) {
            generator.next();
        }

        assert expectedCount == count;
    }
}
