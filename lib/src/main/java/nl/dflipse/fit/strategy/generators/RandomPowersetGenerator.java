package nl.dflipse.fit.strategy.generators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.faultload.faultmodes.HttpError;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.HistoricStore;
import nl.dflipse.fit.strategy.util.PowersetIterator;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;

public class RandomPowersetGenerator implements Generator, FeedbackHandler<Void> {
    private List<FaultMode> modes;
    private List<FaultUid> potentialFaults;
    private PowersetIterator<FaultUid> iterator;

    public RandomPowersetGenerator(List<FaultMode> modes) {
        this.modes = modes;
    }

    public RandomPowersetGenerator() {
        // DelayFault.fromDelayMs(1000),
        // ErrorFault.fromError(HttpError.REQUEST_TIMEOUT)

        this(List.of(
                ErrorFault.fromError(HttpError.SERVICE_UNAVAILABLE)));
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
            System.out
                    .println("[RG] Found " + potentialFaults.size() + " fault points. Will generate " + iterator.size()
                            + " new combinations");
        }

        return null;
    }

    @Override
    public List<Faultload> generate() {
        if (!iterator.hasNext()) {
            return List.of();
        }

        List<FaultUid> nextCombination = iterator.next();

        if (nextCombination.isEmpty()) {
            return List.of();
        }

        List<Faultload> faultLoads = GeneratorUtil.allCombinations(modes, nextCombination);
        return faultLoads;
    }

    @Override
    public void mockFaultUids(List<FaultUid> faultUids) {
        this.potentialFaults = faultUids;
        this.iterator = new PowersetIterator<FaultUid>(potentialFaults, false);
    }

}
