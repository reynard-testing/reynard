package dev.reynard.junit.unit.util;

import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.reynard.junit.strategy.util.Sets;

public class SetsTest {
    @Test
    public void testSubsetOfNil() {
        var set = Set.of();
        var subset = Set.of();

        assertTrue(Sets.isSubsetOf(set, subset));
    }
}
