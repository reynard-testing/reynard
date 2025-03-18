package nl.dflipse.fit.generators;

import java.util.Set;

import org.junit.Test;

import nl.dflipse.fit.strategy.util.MinimalSubsetTrie;

public class SubsetTrieTest {

    @Test
    public void testEmpty() {
        MinimalSubsetTrie<Integer> trie = new MinimalSubsetTrie<>();
        assert !trie.hasSubset(Set.of());
        assert !trie.hasSubset(Set.of(1));
    }

    @Test
    public void testOne() {
        MinimalSubsetTrie<Integer> trie = new MinimalSubsetTrie<>();
        trie.add(Set.of(1));
        // exact match
        assert trie.hasSubset(Set.of(1));
        // superset
        assert trie.hasSubset(Set.of(1, 2));
        // not a subset
        assert !trie.hasSubset(Set.of(2));
        assert !trie.hasSubset(Set.of());
    }

    @Test
    public void testTwo() {
        MinimalSubsetTrie<Integer> trie = new MinimalSubsetTrie<>();
        trie.add(Set.of(1, 2));
        // exact match
        assert trie.hasSubset(Set.of(1, 2));
        // superset
        assert trie.hasSubset(Set.of(1, 2, 3));
        // subsets
        assert !trie.hasSubset(Set.of(1));
        assert !trie.hasSubset(Set.of(2));
        assert !trie.hasSubset(Set.of());
    }

    @Test
    public void testE2E() {
        MinimalSubsetTrie<Integer> trie = new MinimalSubsetTrie<>();

        Set<Integer> set1 = Set.of(1, 2, 3);
        Set<Integer> set2 = Set.of(1, 2);
        Set<Integer> set3 = Set.of(2, 3);
        Set<Integer> set4 = Set.of(2, 4);

        trie.add(set1);
        trie.add(set2);
        trie.add(set3);
        trie.add(set4);

        assert trie.hasSubset(set1);
        assert trie.hasSubset(set2);
        assert trie.hasSubset(set3);
        assert trie.hasSubset(set4);
        assert !trie.hasSubset(Set.of());
        assert !trie.hasSubset(Set.of(3));
        assert !trie.hasSubset(Set.of(1, 3));
        assert trie.hasSubset(Set.of(3, 2));

        Set<Set<Integer>> sets = trie.getAll();

        assert sets.size() == 3;
        for (var set : sets) {
            assert set.equals(set2) || set.equals(set3) || set.equals(set4);
        }
    }
}
