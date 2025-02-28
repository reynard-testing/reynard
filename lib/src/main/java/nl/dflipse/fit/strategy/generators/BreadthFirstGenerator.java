package nl.dflipse.fit.strategy.generators;

import java.util.ArrayList;
import java.util.List;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.HistoricStore;
import nl.dflipse.fit.strategy.util.AllCombinationIterator;
import nl.dflipse.fit.strategy.util.TraceAnalysis;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;

/*
 * Generate all possible combinations of faults in a breadth-first manner.
 * 
 */
public class BreadthFirstGenerator implements Generator, FeedbackHandler<Void> {
    private List<FaultMode> modes;
    private List<FaultUid> potentialFaults;
    private AllCombinationIterator<FaultUid> iterator;

    public BreadthFirstGenerator(List<FaultMode> modes) {
        this.modes = modes;
    }

    public BreadthFirstGenerator() {
        // DelayFault.fromDelayMs(1000),
        // ErrorFault.fromError(ErrorFault.HttpError.REQUEST_TIMEOUT)

        this(List.of(
                ErrorFault.fromError(ErrorFault.HttpError.SERVICE_UNAVAILABLE)));
    }

    @Override
    public Void handleFeedback(FaultloadResult result, HistoricStore history) {
        if (result.isInitial()) {
            potentialFaults = result.trace.getFaultUids(TraversalStrategy.BREADTH_FIRST);

            for (var fault : potentialFaults) {
                System.out.println("[BFS] Found fault: " + fault);
            }

            iterator = new AllCombinationIterator<FaultUid>(potentialFaults);
            System.out
                    .println("[BFS] Found " + potentialFaults.size() + " fault points. Will generate " + iterator.size()
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

        List<Faultload> faultLoads = new ArrayList<>();
        for (var mode : modes) {
            faultLoads.add(new Faultload(GeneratorUtil.asFaults(nextCombination, mode)));
        }

        return faultLoads;
    }

    @Override
    public void mockFaultUids(List<FaultUid> faultUids) {
        this.potentialFaults = faultUids;
        this.iterator = new AllCombinationIterator<FaultUid>(potentialFaults);
    }

}
