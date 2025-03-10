package nl.dflipse.fit.strategy.generators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.HistoricStore;
import nl.dflipse.fit.strategy.util.PairedCombinationsIterator;
import nl.dflipse.fit.strategy.util.PowersetIterator;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;

public class RandomPowersetGenerator implements Generator, FeedbackHandler<Void> {
    private List<FaultMode> modes;
    private List<FaultUid> potentialFaults;
    private PowersetIterator<FaultUid> iterator;
    private PairedCombinationsIterator<FaultUid, FaultMode> pairedIterator = null;

    public RandomPowersetGenerator(List<FaultMode> modes) {
        this.modes = modes;
    }

    @Override
    public Void handleFeedback(FaultloadResult result, HistoricStore history) {
        if (result.isInitial()) {
            potentialFaults = result.trace.getFaultUids(TraversalStrategy.DEPTH_FIRST);

            for (var fault : potentialFaults) {
                System.out.println("[RG] Found fault: " + fault);
            }

            var shuffledFaults = new ArrayList<>(potentialFaults);
            Collections.shuffle(shuffledFaults);
            iterator = new PowersetIterator<FaultUid>(shuffledFaults, false);

            long expectedSize = (long) Math.pow(1 + modes.size(), potentialFaults.size());
            System.out
                    .println("[RG] Found " + potentialFaults.size() + " fault points. Will generate at most "
                            + expectedSize
                            + " new combinations");
        }

        return null;
    }

    @Override
    public List<Faultload> generate() {
        // If we have exhausted the mode-faultUid pairings
        // we need to get the next combination
        if (pairedIterator == null || !pairedIterator.hasNext()) {
            if (!iterator.hasNext()) {
                return List.of();
            }

            List<FaultUid> nextCombination = iterator.next();

            if (nextCombination.isEmpty()) {
                return List.of();
            }

            pairedIterator = new PairedCombinationsIterator<FaultUid, FaultMode>(nextCombination, modes);

            if (!pairedIterator.hasNext()) {
                return List.of();
            }
        }

        // create next faultload
        var nextPairing = pairedIterator.next();
        Set<Fault> faults = nextPairing.stream()
                .map(pair -> new Fault(pair.first(), pair.second()))
                .collect(Collectors.toSet());
        Faultload faultLoad = new Faultload(faults);

        return List.of(faultLoad);
    }

    @Override
    public void mockFaultUids(List<FaultUid> faultUids) {
        this.potentialFaults = faultUids;
        this.iterator = new PowersetIterator<FaultUid>(potentialFaults, false);
    }

}
