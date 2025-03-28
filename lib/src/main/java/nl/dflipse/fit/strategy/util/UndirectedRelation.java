package nl.dflipse.fit.strategy.util;

import java.util.HashSet;
import java.util.Set;

public class UndirectedRelation<X> {
    private final Set<Set<X>> relatedBags = new HashSet<>();

    public void addRelation(X a, X b) {
        // case 1: a and b are already in the relation
        if (areRelated(a, b)) {
            // Do nothing
            return;
        }

        Set<X> hasA = relatedBags.stream()
                .filter(s -> s.contains(a))
                .findFirst().orElse(null);
        Set<X> hasB = relatedBags.stream()
                .filter(s -> s.contains(b))
                .findFirst().orElse(null);

        // case 2: a and b both are related, but not to each other
        if (hasA != null && hasB != null) {
            relatedBags.remove(hasA);
            relatedBags.remove(hasB);
            relatedBags.add(Sets.union(hasA, hasB));
            return;
        }

        // case 3: has a, but not b
        else if (hasA != null) {
            relatedBags.remove(hasA);
            relatedBags.add(Sets.plus(hasA, b));
            return;
        }

        // case 4: has b, but not a
        else if (hasB != null) {
            relatedBags.remove(hasB);
            relatedBags.add(Sets.plus(hasB, a));
            return;
        }

        // case 5: neither a nor b are in the relation
        else {
            relatedBags.add(Set.of(a, b));
        }
    }

    public boolean areRelated(X a, X b) {
        for (Set<X> set : relatedBags) {
            if (set.contains(a) && set.contains(b)) {
                return true;
            }
        }
        return false;
    }

    public Set<X> getRelated(X a) {
        for (Set<X> set : relatedBags) {
            if (set.contains(a)) {
                return set;
            }
        }
        return null;
    }

    public Set<Set<X>> getRelations() {
        return relatedBags;
    }

}
