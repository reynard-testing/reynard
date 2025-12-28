package dev.reynard.junit.unit;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import dev.reynard.junit.strategy.util.Combinatorics;

public class CombinatoricsTest {
    @Test
    public void testPowersetEmpty() {
        List<Integer> emptyList = new ArrayList<>();
        List<List<Integer>> expectedPowerset = List.of(new ArrayList<>());

        List<List<Integer>> powerset = Combinatorics.generatePowerSet(emptyList);

        Assertions.assertIterableEquals(expectedPowerset, powerset);
    }

    @Test
    public void testPowersetSingle() {
        List<Integer> list = List.of(0);
        List<List<Integer>> expectedPowerset = List.of(new ArrayList<>(), List.of(0));

        List<List<Integer>> powerset = Combinatorics.generatePowerSet(list);

        Assertions.assertIterableEquals(expectedPowerset, powerset);
    }

    @Test
    public void testPowersetTwo() {
        List<Integer> list = List.of(0, 1);
        List<List<Integer>> expectedPowerset = List.of(new ArrayList<>(), List.of(0), List.of(1), List.of(0, 1));

        List<List<Integer>> powerset = Combinatorics.generatePowerSet(list);

        Assertions.assertIterableEquals(expectedPowerset, powerset);
    }
}
