package io.github.delanoflipse.fit.suite.unit.util;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import io.github.delanoflipse.fit.suite.strategy.util.Pair;
import io.github.delanoflipse.fit.suite.strategy.util.traversal.TraversalOrder;
import io.github.delanoflipse.fit.suite.strategy.util.traversal.TraversalStrategy;

public class TraversalTest {
    private static List<Pair<String, String>> edges = List.of(
            new Pair<>("A", "B"),
            new Pair<>("B", "C"),
            new Pair<>("A", "D"),
            new Pair<>("D", "E"));

    @Test
    public void testDfsPreOrder() {
        // Example test case for traversal
        // This is a placeholder; actual test logic should be implemented
        // to verify the behavior of the traversal strategy.
        TraversalStrategy<String> strategy = new TraversalStrategy<>(TraversalOrder.DEPTH_FIRST_PRE_ORDER);
        List<String> result = strategy.traverse("A", edges);
        assertEquals(List.of("A", "B", "C", "D", "E"), result);
    }

    @Test
    public void testDfsPostOrder() {
        // Example test case for traversal
        // This is a placeholder; actual test logic should be implemented
        // to verify the behavior of the traversal strategy.
        TraversalStrategy<String> strategy = new TraversalStrategy<>(TraversalOrder.DEPTH_FIRST_POST_ORDER);
        List<String> result = strategy.traverse("A", edges);
        assertEquals(List.of("C", "B", "E", "D", "A"), result);
    }

    @Test
    public void testDfsRevPreOrder() {
        // Example test case for traversal
        // This is a placeholder; actual test logic should be implemented
        // to verify the behavior of the traversal strategy.
        TraversalStrategy<String> strategy = new TraversalStrategy<>(TraversalOrder.DEPTH_FIRST_REVERSE_PRE_ORDER);
        List<String> result = strategy.traverse("A", edges);
        assertEquals(List.of("A", "D", "E", "B", "C"), result);
    }

    @Test
    public void testDfsRevPostOrder() {
        // Example test case for traversal
        // This is a placeholder; actual test logic should be implemented
        // to verify the behavior of the traversal strategy.
        TraversalStrategy<String> strategy = new TraversalStrategy<>(TraversalOrder.DEPTH_FIRST_REVERSE_POST_ORDER);
        List<String> result = strategy.traverse("A", edges);
        assertEquals(List.of("E", "D", "C", "B", "A"), result);
    }

    @Test
    public void testBfs() {
        // Example test case for traversal
        // This is a placeholder; actual test logic should be implemented
        // to verify the behavior of the traversal strategy.
        TraversalStrategy<String> strategy = new TraversalStrategy<>(TraversalOrder.BREADTH_FIRST);
        List<String> result = strategy.traverse("A", edges);
        assertEquals(List.of("A", "B", "D", "C", "E"), result);
    }
}
