package io.github.delanoflipse.fit.suite.unit.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.delanoflipse.fit.suite.strategy.util.TransativeRelation;

public class TransativeRelationTest {
    @Test
    public void testUnknown() {
        TransativeRelation<String> relation = new TransativeRelation<>();
        assertFalse(relation.hasTransativeRelation("A", "C"));
    }

    @Test
    public void testTransative() {
        TransativeRelation<String> relation = new TransativeRelation<>();
        relation.addRelation("A", "B");
        relation.addRelation("B", "C");
        assertTrue(relation.hasTransativeRelation("A", "C"));
    }

    @Test
    public void testSplitPath() {
        TransativeRelation<String> relation = new TransativeRelation<>();
        relation.addRelation("A", "B");
        relation.addRelation("B", "C");
        assertTrue(relation.hasTransativeRelation("A", "C"));
    }

    @Test
    public void testUnrelated() {
        TransativeRelation<String> relation = new TransativeRelation<>();
        relation.addRelation("A", "B");
        relation.addRelation("A", "C");
        assertFalse(relation.hasTransativeRelation("B", "C"));
    }

    @Test
    public void testDirection() {
        TransativeRelation<String> relation = new TransativeRelation<>();
        relation.addRelation("A", "B");
        assertFalse(relation.hasTransativeRelation("B", "A"));
        assertTrue(relation.hasTransativeRelation("A", "B"));
    }

    @Test
    public void testDiamondPattern() {
        TransativeRelation<String> relation = new TransativeRelation<>();
        relation.addRelation("A", "B");
        relation.addRelation("A", "C");
        relation.addRelation("C", "D");
        relation.addRelation("B", "D");
        assertTrue(relation.hasTransativeRelation("A", "D"));
    }

    @Test
    public void testPreventCircular() {
        TransativeRelation<String> relation = new TransativeRelation<>();
        relation.addRelation("A", "B");
        relation.addRelation("B", "C");

        try {
            relation.addRelation("C", "A");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("circular dependency"));
        }
    }

}
