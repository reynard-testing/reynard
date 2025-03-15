package nl.dflipse.fit.strategy.generators;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;
import nl.dflipse.fit.strategy.util.Pair;
import nl.dflipse.fit.strategy.util.PrunablePairedCombinationsIterator;
import nl.dflipse.fit.strategy.util.PrunablePowersetIterator;

public class IncreasingSizeGenerator implements Generator {
    private Set<FaultMode> modes;
    private PrunablePowersetIterator<FaultUid> spaceIterator;
    private PrunablePairedCombinationsIterator<FaultUid, FaultMode> modeIterator;

    private long fidCounter = 0;
    private final DynamicAnalysisStore store;

    public IncreasingSizeGenerator(List<FaultMode> modes) {
        this(Set.copyOf(modes));
    }

    public IncreasingSizeGenerator(Set<FaultMode> modes) {
        this.modes = modes;
        this.store = new DynamicAnalysisStore(modes);
    }

    @Override
    public void reportFaultUids(List<FaultUid> potentialFaults) {
        if (potentialFaults == null || potentialFaults.isEmpty()) {
            return;
        }

        if (this.spaceIterator == null) {
            this.spaceIterator = new PrunablePowersetIterator<FaultUid>(potentialFaults, true);

            for (var prunedFaults : store.getRedundantUidSubsets()) {
                spaceIterator.prune(prunedFaults);
            }

            fidCounter = potentialFaults.size();
            long expectedSize = spaceIterator.size(modes.size());
            System.out.println(
                    "[Generator] Found " + potentialFaults.size() + " fault points. Will generate at most "
                            + expectedSize
                            + " new test cases");
        } else {
            int m = modes.size();
            long oldSize = spaceIterator.size(m);

            for (var fid : potentialFaults) {
                System.out.println("[Generator] Found NEW fault injection point: " + fid);
                this.spaceIterator.add(fid);
            }

            long newSize = spaceIterator.size(m) - oldSize;
            fidCounter += potentialFaults.size();
            System.out.println("[Generator] Added " + newSize + " new test cases");
        }
    }

    private boolean nextSpace() {
        if (!spaceIterator.hasNext()) {
            return false;
        }

        Set<FaultUid> nextSet = spaceIterator.next();
        List<FaultUid> nextCombination = List.copyOf(nextSet);

        if (nextCombination.isEmpty()) {
            return false;
        }

        modeIterator = new PrunablePairedCombinationsIterator<FaultUid, FaultMode>(nextCombination, modes);

        // Prune the mode iterator based on the redundant fault subsets
        for (var prunedFaults : store.getRedundantFaultSubsets()) {
            modeIterator.prune(prunedFaults);
        }

        return modeIterator.hasNext();
    }

    private Set<Fault> getFaultsFromPairing(List<Pair<FaultUid, FaultMode>> pairing) {
        return pairing.stream()
                .map(pair -> new Fault(pair.first(), pair.second()))
                .collect(Collectors.toSet());
    }

    @Override
    public List<Faultload> generate() {
        // If we have exhausted the mode-faultUid pairings
        // we need to get the next fault injection space point (subset of fault
        // injection points)
        if (modeIterator == null || !modeIterator.hasNext()) {
            boolean canContinue = nextSpace();
            if (!canContinue) {
                return List.of();
            }
        }

        // create next faultload
        var nextPairing = modeIterator.next();
        Set<Fault> faults = getFaultsFromPairing(nextPairing);
        Faultload faultLoad = new Faultload(faults);

        // if we should skip this specific faultload
        // then we should generate the next one
        if (store.hasFaultload(faultLoad)) {
            return generate();
        }

        return List.of(faultLoad);
    }

    @Override
    public long pruneFaultUidSubset(Set<FaultUid> subset) {
        boolean isNew = store.ignoreFaultUidSubset(subset);

        // if (!isNew) {
        // return 0;
        // }

        if (isNew && spaceIterator != null) {
            spaceIterator.prune(subset);
        }

        int subsetSize = subset.size();
        return subsetSpaceSize(subsetSize, fidCounter - subsetSize);
    }

    @Override
    public long pruneFaultSubset(Set<Fault> subset) {
        var prunableSet = store.ignoreFaultSubset(subset);
        boolean isNew = prunableSet != null;

        // if (!isNew) {
        // return 0;
        // }

        if (isNew && modeIterator != null) {
            modeIterator.prune(prunableSet);
        }

        int subsetSize = subset.size();
        // All subsets that contain this subset, the faults are fixed.
        // E.g., only 1 way to assign the subset items
        // so the others (the front) can be assigned in any way
        return subsetSpaceSize(0, fidCounter - subsetSize);
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

    @Override
    public long pruneMixedSubset(Set<Fault> faultSubset, Set<FaultUid> uidSubset) {
        var combinationsIterator = new PrunablePairedCombinationsIterator<>(List.copyOf(uidSubset), modes);

        long sum = 0;

        while (combinationsIterator.hasNext()) {
            var nextPairing = combinationsIterator.next();
            Set<Fault> faults = getFaultsFromPairing(nextPairing);
            faults.addAll(faultSubset);
            sum += this.pruneFaultSubset(faults);
        }

        return sum;
    }

}
