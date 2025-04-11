package nl.dflipse.fit.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FailureMode;
import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.generators.IncreasingSizeGenerator;
import nl.dflipse.fit.strategy.pruners.PruneDecision;
import nl.dflipse.fit.strategy.pruners.Pruner;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;

public class StrategyRunner {
    private DynamicAnalysisStore store;
    private Generator generator = null;

    private final List<FeedbackHandler> analyzers = new ArrayList<>();
    private final List<Pruner> pruners = new ArrayList<>();
    private final List<Reporter> reporters = new ArrayList<>();
    private final List<String> componentNames = new ArrayList<>();

    public StrategyStatistics statistics = new StrategyStatistics(this);

    private boolean intialRun = true;

    private boolean withPayloadMasking = false;
    private boolean withBodyHashing = false;
    private boolean withLogHeader = false;
    private int withGetDelayMs = 0;
    private long testCasesLeft = -1;

    private final Logger logger = LoggerFactory.getLogger(StrategyRunner.class);

    public StrategyRunner(List<FailureMode> modes) {
        this.store = new DynamicAnalysisStore(modes);
    }

    public StrategyRunner withPayloadMasking() {
        withPayloadMasking = true;
        return this;
    }

    public StrategyRunner withBodyHashing() {
        withBodyHashing = true;
        return this;
    }

    public StrategyRunner withLogHeader() {
        withLogHeader = true;
        return this;
    }

    public StrategyRunner withGetDelay(int ms) {
        withGetDelayMs = ms;
        return this;
    }

    public StrategyRunner withMaxTestCases(long max) {
        testCasesLeft = max;
        return this;
    }

    public StrategyRunner withComponent(Object component) {
        List<String> attributes = new ArrayList<>();
        String className = component.getClass().getSimpleName();

        if (component instanceof Generator gen) {
            this.generator = gen;
            attributes.add("Generator");
        }

        if (component instanceof FeedbackHandler analyzer) {
            analyzers.add(analyzer);
            attributes.add("Analyzer");
        }

        if (component instanceof Pruner pruner) {
            pruners.add(pruner);
            attributes.add("Pruner");
        }

        if (component instanceof Reporter reporter) {
            reporters.add(reporter);
            attributes.add("Reporter");
        }

        String name = attributes.isEmpty()
                ? className
                : className + "(" + String.join(", ", attributes) + ")";

        componentNames.add(name);
        return this;
    }

    public boolean hasGenerators() {
        return generator != null;
    }

    public Generator getGenerator() {
        return generator;
    }

    public DynamicAnalysisStore getStore() {
        return store;
    }

    public List<Reporter> getReporters() {
        return reporters;
    }

    public List<String> getComponentNames() {
        return componentNames;
    }

    private Faultload getNextFaultload() {
        if (intialRun) {
            intialRun = false;
            logger.info("Starting with initial empty faultload!");
            return new Faultload(Set.of());
        }

        // Queue is empty, no more faultloads to run
        // Test case limit reached
        if (testCasesLeft == 0) {
            logger.warn("Reached test case limit, stopping!");
            return null;
        } else if (testCasesLeft > 0) {
            testCasesLeft--;
        }

        Faultload faultload = generateAndPruneTillNext();

        if (faultload == null) {
            logger.info("No new faultload generated, stopping!");
            return null;
        }

        return faultload;
    }

    private TrackedFaultload toTracked(Faultload faultload) {
        // Wrap the faultload in a tracked faultload
        // And prepare its properties
        var tracked = new TrackedFaultload(faultload);

        if (withPayloadMasking) {
            tracked.withMaskPayload();
        }

        if (withBodyHashing) {
            tracked.withBodyHashing();
        }

        if (withLogHeader) {
            tracked.withHeaderLog();
        }

        if (withGetDelayMs > 0) {
            boolean isInitial = tracked.getFaultload().faultSet().isEmpty();
            // Wait longer on the initial run, to ensure we got everyting
            int multiplier = isInitial ? 4 : 1;
            tracked.withGetDelay(multiplier * withGetDelayMs);
        }

        return tracked;
    }

    public TrackedFaultload nextFaultload() {
        Faultload faultload = getNextFaultload();

        if (faultload == null) {
            return null;
        }

        return toTracked(faultload);
    }

    public Faultload generateAndPruneTillNext() {
        int orders = 2;
        int generateCount = 0;
        int pruneCount = 0;

        Faultload next = null;

        while (true) {
            // Generate new faultloads
            Faultload generated = generate();
            generateCount++;

            // Generator is exhausted
            if (generated == null) {
                break;
            }

            PruneDecision decision = prune(generated);
            boolean shouldPrune = decision == PruneDecision.PRUNE ||
                    decision == PruneDecision.PRUNE_SUBTREE;

            if (!shouldPrune) {
                // We found a new faultload!
                next = generated;
                break;
            }
            // Log some progress, in case we are generating and pruning a lot
            pruneCount++;
            long order = (long) Math.pow(10, orders);
            if (generateCount > order) {
                logger.info("Progress: generated and pruned >" + order + " faultloads");
                orders++;
            }
        }

        if (generator instanceof IncreasingSizeGenerator gen) {
            logger.info("Generator queue size: {}", gen.getQueuSize());
        }

        logger.info("Generated {} and pruned {} faultloads", generateCount, pruneCount);
        return next;
    }

    public void registerTime(TrackedFaultload faultload) {
        statistics.registerTime(faultload.timer);
    }

    public void handleResult(FaultloadResult result) {
        logger.info("Analyzing result of running faultload with traceId=" + result.trackedFaultload.getTraceId());
        if (result.isInitial() && !result.trace.getReportedFaults().isEmpty()) {
            logger.error("The intial happy path should be fault free, but its not! Stopping the test suite.");
            logger.error("Found {}", result.trace.getReportedFaults());
            testCasesLeft = 0;
            return;
        }

        analyze(result);
        logger.info("Selecting next faultload");
    }

    public Faultload generate() {
        if (generator == null) {
            throw new RuntimeException("[Strategy] No generators are available, make sure to register at least one!");
        }

        var generated = generator.generate();

        if (generated != null) {
            statistics.incrementGenerator("Generated", 1);
        }

        return generated;
    }

    public void analyze(FaultloadResult result) {
        result.trackedFaultload.timer.start("StrategyRunner.analyze");

        for (var analyzer : analyzers) {
            String name = analyzer.getClass().getSimpleName();
            String tag = name + ".handleFeedback<Analyzer>";
            result.trackedFaultload.timer.start(tag);

            FeedbackContext context = new FeedbackContextProvider(this, analyzer.getClass(), result);
            analyzer.handleFeedback(result, context);

            result.trackedFaultload.timer.stop(tag);
        }

        result.trackedFaultload.timer.stop("StrategyRunner.analyze");
    }

    public PruneDecision prune(Faultload faultload) {
        boolean shouldPrune = false;
        boolean shouldPruneSubtree = false;

        for (Pruner pruner : pruners) {
            var decision = pruner.prune(faultload);
            switch (decision) {
                case PRUNE -> {
                    statistics.incrementPruner(pruner.getClass().getSimpleName(), 1);
                    shouldPrune = true;
                }
                case PRUNE_SUBTREE -> {
                    statistics.incrementPruner(pruner.getClass().getSimpleName(), 1);
                    new FeedbackContextProvider(this, pruner.getClass(), null)
                            .pruneFaultSubset(faultload.faultSet());
                    shouldPrune = true;
                    shouldPruneSubtree = true;
                }
                case KEEP -> {
                }
            }
        }

        if (shouldPrune) {
            statistics.incrementPruned(1);
        }

        if (shouldPruneSubtree) {
            return PruneDecision.PRUNE_SUBTREE;
        } else if (shouldPrune) {
            return PruneDecision.PRUNE;
        } else {
            return PruneDecision.KEEP;
        }
    }
}
