package dev.reynard.junit.instrumentation.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LRUCache<K, V> extends HashMap<K, V> {
    private static final int MAX_CAPACITY = 100;
    private final List<K> keys = new ArrayList<>();

    public LRUCache(int maxCapacity) {
        super();
    }

    @Override
    public void clear() {
        super.clear();
        keys.clear();
    }

    @Override
    public V put(K key, V value) {
        if (keys.size() > MAX_CAPACITY) {
            K lruKey = keys.remove(0);
            super.remove(lruKey);
        }

        keys.add(key);
        return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
    }

    @Override
    public V remove(Object key) {
        keys.remove(key);
        return super.remove(key);
    }
}
