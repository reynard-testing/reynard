package nl.dflipse.fit.strategy.generators;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;
import nl.dflipse.fit.strategy.util.PairedCombinationsIterator;
import nl.dflipse.fit.strategy.util.PrunablePowersetIterator;

public class IncreasingSizeGenerator implements Generator {
    private List<FaultMode> modes;
    private PrunablePowersetIterator<FaultUid> spaceIterator;
    private PairedCombinationsIterator<FaultUid, FaultMode> modeIterator;

    private DynamicAnalysisStore store = new DynamicAnalysisStore();

    public IncreasingSizeGenerator(List<FaultMode> modes) {
        this.modes = modes;
    }

    @Override
    public void reportFaultUids(List<FaultUid> potentialFaults) {
        if (this.spaceIterator == null) {
            this.spaceIterator = new PrunablePowersetIterator<FaultUid>(potentialFaults, true);
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

        modeIterator = new PairedCombinationsIterator<FaultUid, FaultMode>(nextCombination, modes);

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

        return List.of(faultLoad);
    }

    @Override
    public void ignoreFaultUidSubset(Set<FaultUid> subset) {
        store.ignoreFaultUidSubset(subset);
        spaceIterator.prune(subset);
    }

    @Override
    public void ignoreFaultSubset(Set<Fault> subset) {
        var prunableSet = store.ignoreFaultSubset(subset);
        modeIterator.prune(prunableSet);
    }

}
