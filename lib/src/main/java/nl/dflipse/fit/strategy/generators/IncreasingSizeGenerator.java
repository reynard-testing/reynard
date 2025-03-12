package nl.dflipse.fit.strategy.generators;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;
import nl.dflipse.fit.strategy.util.PrunablePairedCombinationsIterator;
import nl.dflipse.fit.strategy.util.PrunablePowersetIterator;

public class IncreasingSizeGenerator implements Generator {
    private Set<FaultMode> modes;
    private PrunablePowersetIterator<FaultUid> spaceIterator;
    private PrunablePairedCombinationsIterator<FaultUid, FaultMode> modeIterator;

    private int fidCounter = 0;
    private DynamicAnalysisStore store = new DynamicAnalysisStore();

    public IncreasingSizeGenerator(List<FaultMode> modes) {
        this.modes = Set.copyOf(modes);
    }

    public IncreasingSizeGenerator(Set<FaultMode> modes) {
        this.modes = modes;
    }

    @Override
    public void reportFaultUids(List<FaultUid> potentialFaults) {
        if (this.spaceIterator == null) {
            this.spaceIterator = new PrunablePowersetIterator<FaultUid>(potentialFaults, true);
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
            fidCounter += newSize;
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

        if (!modeIterator.hasNext()) {
            return false;
        }

        return true;
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
        Set<Fault> faults = nextPairing.stream()
                .map(pair -> new Fault(pair.first(), pair.second()))
                .collect(Collectors.toSet());
        Faultload faultLoad = new Faultload(faults);

        // if we should skip this specific faultload
        // then we should generate the next one
        if (store.hasFaultload(faultLoad)) {
            return generate();
        }

        return List.of(faultLoad);
    }

    @Override
    public void ignoreFaultUidSubset(Set<FaultUid> subset) {
        boolean isNew = store.ignoreFaultUidSubset(subset);
        if (isNew) {
            spaceIterator.prune(subset);
        }
    }

    @Override
    public void ignoreFaultSubset(Set<Fault> subset) {
        var prunableSet = store.ignoreFaultSubset(subset);
        if (modeIterator != null && prunableSet != null) {
            modeIterator.prune(prunableSet);
        }
    }

    @Override
    public void ignoreFaultload(Faultload faultload) {
        store.ignoreFaultload(faultload);
    }

    @Override
    public long spaceSize() {
        return (long) Math.pow(1 + modes.size(), fidCounter) - 1;
    }

}
