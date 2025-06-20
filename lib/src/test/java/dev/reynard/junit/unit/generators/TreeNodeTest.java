package dev.reynard.junit.unit.generators;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import dev.reynard.junit.strategy.components.generators.TreeNode;
import dev.reynard.junit.util.FailureModes;
import dev.reynard.junit.util.FaultInjectionPoints;
import dev.reynard.junit.util.FaultsBuilder;

public class TreeNodeTest {
        FaultsBuilder builder = new FaultsBuilder(
                        FaultInjectionPoints.getPoints(3),
                        FailureModes.getModes(2));

        @Test
        public void testEqualityTrivial() {
                var node1 = new TreeNode(
                                List.of());
                var node2 = new TreeNode(
                                List.of());
                assert node1.equals(node2) : "Empty nodes should be equal";
                assert node1.hashCode() == node2.hashCode() : "hashcodes should be equal";
        }

        @Test
        public void testEqualityOne() {
                var node1 = new TreeNode(
                                List.of(
                                                builder.get(0, 0)));
                var node2 = new TreeNode(
                                List.of(builder.get(0, 0)));
                assert node1.equals(node2) : "nodes should be equal";
                assert node1.hashCode() == node2.hashCode() : "hashcodes should be equal";
        }

        @Test
        public void testEqualityAsSet() {
                var node1 = new TreeNode(
                                List.of(
                                                builder.get(0, 0), builder.get(0, 1)));
                var node2 = new TreeNode(
                                List.of(builder.get(0, 1), builder.get(0, 0)));
                assert node1.equals(node2) : "nodes should be equal";
                assert node1.hashCode() == node2.hashCode() : "hashcodes should be equal";
        }

        @Test
        public void testMapLookup() {
                Map<TreeNode, Integer> mapping = new LinkedHashMap<>();
                var node1 = new TreeNode(
                                List.of(
                                                builder.get(0, 0), builder.get(0, 1)));
                var node2 = new TreeNode(
                                List.of(builder.get(0, 1), builder.get(0, 0)));
                mapping.put(node1, 1);
                assert mapping.containsKey(node2) : "map should contain node2";
                assert mapping.get(node2) == 1 : "map should return the same value for node2";
        }
}
