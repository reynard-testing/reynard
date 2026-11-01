package dev.reynard.junit.unit.instrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

import dev.reynard.junit.instrumentation.controller.LRUCache;

public class LRUCacheTest {
    @Test
    public void testRespectsConfiguredCapacity() {
        LRUCache<Integer, Integer> cache = new LRUCache<>(3);

        for (int i = 0; i < 5; i++) {
            cache.put(i, i);
        }

        assertEquals(3, cache.size());
        assertFalse(cache.containsKey(0));
        assertFalse(cache.containsKey(1));
        assertTrue(cache.containsKey(4));
    }

    @Test
    public void testEvictsLeastRecentlyUsedNotLeastRecentlyInserted() {
        LRUCache<Integer, Integer> cache = new LRUCache<>(2);

        cache.put(1, 1);
        cache.put(2, 2);
        cache.get(1); // touch 1, so 2 becomes the least recently used
        cache.put(3, 3);

        assertEquals(2, cache.size());
        assertTrue(cache.containsKey(1));
        assertFalse(cache.containsKey(2));
        assertTrue(cache.containsKey(3));
    }

    @Test
    public void testRepeatedPutOfSameKeyDoesNotCorruptEviction() {
        LRUCache<Integer, Integer> cache = new LRUCache<>(2);

        cache.put(1, 1);
        cache.put(1, 1);
        cache.put(2, 2);
        cache.put(3, 3);

        assertEquals(2, cache.size());
        assertFalse(cache.containsKey(1));
        assertTrue(cache.containsKey(2));
        assertTrue(cache.containsKey(3));
    }
}
