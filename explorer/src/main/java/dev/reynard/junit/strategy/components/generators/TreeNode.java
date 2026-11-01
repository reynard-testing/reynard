package dev.reynard.junit.strategy.components.generators;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.strategy.util.Sets;

public record TreeNode(List<Fault> value) {
    // For equality, the list is a set
    @Override
    public final boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (o instanceof TreeNode other) {
            return Sets.areEqual(value, other.value);
        }

        return false;
    }

    public Set<Fault> asSet() {
        return new LinkedHashSet<>(value);
    }

    // In the hashcode, we use the set representation
    // So in a hashset, our equality check still works
    @Override
    public final int hashCode() {
        return asSet().hashCode();
    }
}
