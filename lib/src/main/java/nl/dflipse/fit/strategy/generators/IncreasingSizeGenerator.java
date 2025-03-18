package nl.dflipse.fit.strategy.generators;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;
import nl.dflipse.fit.strategy.util.PrunableGenericPowersetTreeIterator;
import nl.dflipse.fit.strategy.util.Sets;

public class IncreasingSizeGenerator implements Generator {
    private Set<FaultMode> modes;
    private PrunableGenericPowersetTreeIterator<Fault, FaultUid> iterator;

    private long fidCounter = 0;
    private final DynamicAnalysisStore store;

    public IncreasingSizeGenerator(List<FaultMode> modes) {
        this(Set.copyOf(modes));
    }

    public IncreasingSizeGenerator(Set<FaultMode> modes) {
        this.modes = modes;
        this.store = new DynamicAnalysisStore(modes);
    }

    private Set<Fault> faultUidtoFaults(FaultUid uid) {
        return modes.stream()
                .map(mode -> new Fault(uid, mode))
                .collect(Collectors.toSet());
    }

    @Override
    public void reportFaultUids(List<FaultUid> potentialFaults) {
        if (potentialFaults == null || potentialFaults.isEmpty()) {
            return;
        }

        if (this.iterator == null) {
            this.iterator = new PrunableGenericPowersetTreeIterator<Fault, FaultUid>(potentialFaults,
                    this::faultUidtoFaults, true);

            for (var prunedFaults : store.getRedundantFaultSubsets()) {
                var faultSet = DynamicAnalysisStore.pairsToFaults(prunedFaults);
                iterator.prune(faultSet);
            }

            fidCounter = potentialFaults.size();
            long expectedSize = iterator.size(modes.size());
            System.out.println(
                    "[Generator] Found " + potentialFaults.size() + " fault points. Will generate at most "
                            + expectedSize
                            + " new test cases");
        } else {
            int m = modes.size();
            long oldSize = iterator.size(m);

            for (var fid : potentialFaults) {
                System.out.println("[Generator] Found NEW fault injection point: " + fid);
                this.iterator.add(fid);
            }

            long newSize = iterator.size(m) - oldSize;
            fidCounter += potentialFaults.size();
            System.out.println("[Generator] Added " + newSize + " new test cases");
        }
    }

    @Override
    public List<Faultload> generate() {
        // If we have exhausted the mode-faultUid pairings
        // we need to get the next fault injection space point (subset of fault
        // injection points)
        if (iterator == null || !iterator.hasNext()) {
            return List.of();
        }

        // create next faultload
        var nextFaults = iterator.next();
        Faultload faultLoad = new Faultload(nextFaults);

        // if we should skip this specific faultload
        // then we should generate the next one
        if (store.hasFaultload(faultLoad)) {
            return generate();
        }

        return List.of(faultLoad);
    }

    private Set<Set<Fault>> allFaults(List<FaultUid> uids) {
        return allFaults(uids, Set.of());
    }

    private Set<Set<Fault>> allFaults(List<FaultUid> uids, Set<Fault> current) {
        if (uids.isEmpty()) {
            return Set.of(current);
        }

        var head = uids.get(0);
        var tail = uids.subList(1, uids.size());
        Set<Fault> additions = faultUidtoFaults(head);
        Set<Set<Fault>> allSets = new HashSet<>();
        for (var addition : additions) {
            Set<Fault> newCurrent = Sets.plus(current, addition);
            allSets.addAll(allFaults(tail, newCurrent));
        }

        return allSets;
    }

    @Override
    public long pruneFaultUidSubset(Set<FaultUid> subset) {
        long sum = 0;
        for (Set<Fault> prunedSet : allFaults(List.copyOf(subset))) {
            sum += this.pruneFaultSubset(prunedSet);
        }

        return sum;
    }

    @Override
    public long pruneFaultSubset(Set<Fault> subset) {
        var prunableSet = store.ignoreFaultSubset(subset);
        boolean isNew = prunableSet != null;

        // if (!isNew) {
        // return 0;
        // }

        if (isNew && iterator != null) {
            iterator.prune(subset);
        }

        int subsetSize = subset.size();
        // All subsets that contain this subset, the faults are fixed.
        // E.g., only 1 way to assign the subset items
        // so the others (the front) can be assigned in any way
        return subsetSpaceSize(0, fidCounter - subsetSize);
    }

    @Override
    public long pruneMixedSubset(Set<Fault> faultSubset, Set<FaultUid> uidSubset) {
        long sum = 0;
        for (Set<Fault> prunedSet : allFaults(List.copyOf(uidSubset), faultSubset)) {
            sum += this.pruneFaultSubset(prunedSet);
        }

        return sum;
    }

    @Override
    public long pruneFaultload(Faultload faultload) {
        store.ignoreFaultload(faultload);
        return 1;
    }

    private long subsetSpaceSize(long subsetSize, long frontSize) {
        return subsetSpaceSize(modes.size(), subsetSize, frontSize);
    }

    private long subsetSpaceSize(long m, long subsetSize, long frontSize) {
        return (long) (Math.pow(m, subsetSize) * Math.pow(1 + m, frontSize));
    }

    @Override
    public long spaceSize() {
        return subsetSpaceSize(0, fidCounter);
    }

    @Override
    public Set<FaultMode> getFaultModes() {
        return modes;
    }

    public int getElements() {
        return iterator.elementCount();
    }

    public DynamicAnalysisStore getStore() {
        return store;
    }

}
