package nl.dflipse.fit.strategy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.modes.FailureMode;
import nl.dflipse.fit.strategy.components.FeedbackContext;
import nl.dflipse.fit.strategy.components.FeedbackContextProvider;
import nl.dflipse.fit.strategy.components.FeedbackHandler;
import nl.dflipse.fit.strategy.components.PruneContextProvider;
import nl.dflipse.fit.strategy.components.PruneDecision;
import nl.dflipse.fit.strategy.components.Pruner;
import nl.dflipse.fit.strategy.components.Reporter;
import nl.dflipse.fit.strategy.components.generators.Generator;
import nl.dflipse.fit.strategy.components.generators.IncreasingSizeGenerator;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.testutil.TaggedTimer;

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
    private long maxTimeS = 0;
    private long testCasesLeft = -1;
    private long startTime = 0;

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

    public StrategyRunner withMaxTimeS(long timeout) {
        maxTimeS = timeout;
        startTime = System.currentTimeMillis();
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

        if (maxTimeS > 0) {
            long secs = (long) (System.currentTimeMillis() - startTime) / 1000;
            if (secs > maxTimeS) {
                logger.warn("Reached time limit, stopping!");
                return null;
            }
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

            if (decision == PruneDecision.KEEP) {
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

    public void registerTime(TaggedTimer timer) {
        statistics.registerTime(timer);
    }

    public void handleResult(FaultloadResult result) {
        store.addHistoricResult(result.trace.getInjectedFaults(), result.trace.getBehaviours());

        logger.info("Analyzing result of running faultload with traceId=" + result.trackedFaultload.getTraceId());
        if (result.isInitial() && !result.trace.getReportedFaults().isEmpty()) {
            logger.error("The intial happy path should be fault free, but its not! Stopping the test suite.");
            logger.error("Found {}", result.trace.getReportedFaults());
            testCasesLeft = 0;
            return;
        }

        // analyze the result
        analyze(result);
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

            FeedbackContext context = new FeedbackContextProvider(this, analyzer.getClass());
            analyzer.handleFeedback(result, context);

            result.trackedFaultload.timer.stop(tag);
        }

        result.trackedFaultload.timer.stop("StrategyRunner.analyze");
    }

    public PruneDecision prune(Set<Fault> fs) {
        return prune(new Faultload(fs));
    }

    public PruneDecision prune(Faultload faultload) {
        PruneDecision pruneDecision = PruneDecision.KEEP;
        // attributed pruners. A prune_subtree > prune, so we only store
        // the pruners of the most impactfull class
        Set<Pruner> attributed = new LinkedHashSet<>();

        for (Pruner pruner : pruners) {
            PruneContextProvider context = new PruneContextProvider(this, pruner.getClass());
            PruneDecision decision = pruner.prune(faultload, context);
            switch (decision) {
                case PRUNE -> {
                    if (pruneDecision == PruneDecision.KEEP) {
                        pruneDecision = PruneDecision.PRUNE;
                    }
                    attributed.add(pruner);

                }
                case PRUNE_SUPERSETS -> {
                    pruneDecision = PruneDecision.PRUNE_SUPERSETS;
                    attributed.add(pruner);
                }
                case KEEP -> {
                }
            }
        }

        if (attributed.size() == 1) {
            Pruner attributedPruner = Sets.getOnlyElement(attributed);
            String name = attributedPruner.getClass().getSimpleName();
            logger.debug("Pruner {} uniquely pruned ({}) the faultload {}", name, pruneDecision, faultload);
            statistics.incrementPruner(name, 1);
        } else if (!attributed.isEmpty()) {
            String names = attributed.stream()
                    .map(Pruner::getClass)
                    .map(Class::getSimpleName)
                    .sorted()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("Unknown");
            logger.debug("Pruners {} pruned ({}) the faultload {}", names, pruneDecision, faultload);
            statistics.incrementPruner(names, 1);
        }

        if (pruneDecision != PruneDecision.KEEP) {
            statistics.incrementPruned(1);
        }

        return pruneDecision;
    }
}
