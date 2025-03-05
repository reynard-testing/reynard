package nl.dflipse.fit.generators;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import nl.dflipse.fit.strategy.generators.PowersetGenerator;

public class PowersetTree {

    private List<Set<Integer>> expandComplete(PowersetGenerator<Integer> generator) {
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
        PowersetGenerator<Integer> generator = new PowersetGenerator<>(null);
        assert generator.hasNext() == false;
        assertEqual(List.of(), expandComplete(generator));
    }

    @Test
    public void testEmpty() {
        PowersetGenerator<Integer> generator = new PowersetGenerator<>(new ArrayList<>());
        assertEqual(List.of(Set.of()), expandComplete(generator));
    }

    @Test
    public void testOne() {
        List<Integer> elements = List.of(1);
        PowersetGenerator<Integer> generator = new PowersetGenerator<>(elements);
        var expanded = expandComplete(generator);
        assert expanded.size() == expectedSize(elements.size());
        assertEqual(List.of(Set.of(), Set.of(1)), expanded);
    }

    @Test
    public void testTwo() {
        List<Integer> elements = List.of(1, 2);
        PowersetGenerator<Integer> generator = new PowersetGenerator<>(elements);
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
        PowersetGenerator<Integer> generator = new PowersetGenerator<>(elements);
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
        PowersetGenerator<Integer> generator = new PowersetGenerator<>(elements);
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
        PowersetGenerator<Integer> generator = new PowersetGenerator<>(elements);
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
}
