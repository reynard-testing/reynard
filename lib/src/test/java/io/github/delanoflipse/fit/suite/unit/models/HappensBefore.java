package io.github.delanoflipse.fit.suite.unit.models;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import org.junit.jupiter.api.Test;

import io.github.delanoflipse.fit.suite.faultload.FaultInjectionPoint;

public class HappensBefore {

    @Test
    public void testBeforeNull() {
        Map<String, Integer> cs1 = Map.of();
        Map<String, Integer> cs2 = Map.of();
        boolean actual = FaultInjectionPoint.isBefore(cs1, cs2);
        assertEquals(false, actual);
    }

    @Test
    public void testBeforeEqual() {
        Map<String, Integer> cs1 = Map.of("x", 1);
        Map<String, Integer> cs2 = Map.of("x", 1);
        boolean actual = FaultInjectionPoint.isBefore(cs1, cs2);
        assertEquals(false, actual);
    }

    @Test
    public void testBeforeSimple() {
        Map<String, Integer> cs1 = Map.of("x", 1);
        Map<String, Integer> cs2 = Map.of("x", 2);
        boolean actual = FaultInjectionPoint.isBefore(cs1, cs2);
        assertEquals(true, actual);
    }

    @Test
    public void testBeforeMultiple() {
        Map<String, Integer> cs1 = Map.of("x", 1, "y", 1);
        Map<String, Integer> cs2 = Map.of("x", 2, "y", 1);
        boolean actual = FaultInjectionPoint.isBefore(cs1, cs2);
        assertEquals(true, actual);
    }

    @Test
    public void testBeforeMissing() {
        Map<String, Integer> cs1 = Map.of("x", 1);
        Map<String, Integer> cs2 = Map.of("x", 1, "y", 1);
        boolean actual = FaultInjectionPoint.isBefore(cs1, cs2);
        assertEquals(true, actual);
    }

    @Test
    public void testAfterSimple() {
        Map<String, Integer> cs1 = Map.of("x", 2);
        Map<String, Integer> cs2 = Map.of("x", 1);
        boolean actual = FaultInjectionPoint.isBefore(cs1, cs2);
        assertEquals(false, actual);
    }

    @Test
    public void testIncomparible() {
        Map<String, Integer> cs1 = Map.of("x", 1, "y", 2);
        Map<String, Integer> cs2 = Map.of("x", 2, "y", 1);
        boolean actual = FaultInjectionPoint.isBefore(cs1, cs2);
        assertEquals(false, actual);
    }
}
