package io.github.delanoflipse.fit.suite.util;

import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.delanoflipse.fit.suite.strategy.util.Sets;

public class SetsTest {
    @Test
    public void testSubsetOfNil() {
        var set = Set.of();
        var subset = Set.of();

        assertTrue(Sets.isSubsetOf(set, subset));
    }
}
