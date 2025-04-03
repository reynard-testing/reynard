package nl.dflipse.fit.strategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.pruners.Pruner;
import nl.dflipse.fit.strategy.util.Pair;

public class StrategyRunner {
    public final List<Faultload> queue = new ArrayList<>();

    private Generator generator = null;

    private final List<FeedbackHandler> analyzers = new ArrayList<>();
    private final List<Pruner> pruners = new ArrayList<>();
    private final List<Reporter> reporters = new ArrayList<>();
    private final List<String> componentNames = new ArrayList<>();

    public StrategyStatistics statistics = new StrategyStatistics(this);

    private boolean withPayloadMasking = false;
    private boolean withBodyHashing = false;
    private boolean withLogHeader = false;
    private int withGetDelayMs = 0;
    private long testCasesLeft = -1;

    private final Logger logger = LoggerFactory.getLogger(StrategyRunner.class);

    public StrategyRunner() {
        // Initialize the queue with an empty faultload
        queue.add(new Faultload(Set.of()));
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

    public List<Reporter> getReporters() {
        return reporters;
    }

    public List<String> getComponentNames() {
        return componentNames;
    }

    public TrackedFaultload nextFaultload() {
        // Queue is empty, no more faultloads to run
        if (queue.isEmpty()) {
            return null;
        }

        // Test case limit reached
        if (testCasesLeft == 0) {
            logger.warn("Reached test case limit, stopping!");
            return null;
        } else if (testCasesLeft > 0) {
            testCasesLeft--;
        }

        // Get the next faultload from the queue
        var queued = queue.remove(0);

        // Wrap the faultload in a tracked faultload
        // And prepare its properties
        var tracked = new TrackedFaultload(queued);
        boolean isInitial = tracked.getFaultload().faultSet().isEmpty();

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
            // Wait longer on the initial run, to ensure we got everyting
            int multiplier = isInitial ? 4 : 1;
            tracked.withGetDelay(multiplier * withGetDelayMs);
        }

        return tracked;
    }

    public Pair<Integer, Integer> generateAndPrune() {
        // Generate new faultloads
        List<Faultload> newFaultloads = generate();

        queue.addAll(newFaultloads);

        // Prune the queue
        int pruned = prune();
        return new Pair<>(newFaultloads.size(), pruned);
    }

    public Pair<Integer, Integer> generateAndPruneTillNext() {
        int orders = 2;
        int generated = 0;
        int pruned = 0;

        while (true) {
            var res = generateAndPrune();
            int newFaultloads = res.getFirst();

            if (newFaultloads == 0) {
                break;
            }

            generated += newFaultloads;
            pruned += res.getSecond();

            long order = (long) Math.pow(10, orders);
            if (generated > order) {
                logger.info("Progress: generated and pruned >" + order + " faultloads");
                orders++;
            }

            // Keep generating and pruning until we have new faultloads in the queue
            if (!queue.isEmpty()) {
                break;
            }
        }

        return new Pair<>(generated, pruned);
    }

    public void registerTime(TrackedFaultload faultload) {
        statistics.registerTime(faultload.timer);
    }

    public void handleResult(FaultloadResult result) {
        logger.info("Analyzing result of running faultload with traceId=" + result.trackedFaultload.getTraceId());
        analyze(result);
        logger.info("Selecting next faultload");

        result.trackedFaultload.timer.start("StrategyRunner.generateAndPrune");
        var res = generateAndPruneTillNext();
        int generated = res.getFirst();
        int pruned = res.getSecond();
        result.trackedFaultload.timer.stop("StrategyRunner.generateAndPrune");

        logger.info("Generated " + generated + " new faultloads, pruned " + pruned + " faultloads");
    }

    public List<Faultload> generate() {
        if (generator == null) {
            throw new RuntimeException("[Strategy] No generators are available, make sure to register at least one!");
        }

        var generated = generator.generate();
        statistics.incrementGenerator("Generated", generated.size());

        return generated;
    }

    public void analyze(FaultloadResult result) {
        result.trackedFaultload.timer.start("StrategyRunner.analyze");

        for (var analyzer : analyzers) {
            String name = analyzer.getClass().getSimpleName();
            String tag = name + ".handleFeedback<Analyzer>";
            result.trackedFaultload.timer.start(tag);

            FeedbackContext context = new FeedbackContext(this, analyzer.getClass(), result);
            analyzer.handleFeedback(result, context);

            result.trackedFaultload.timer.stop(tag);
        }

        result.trackedFaultload.timer.stop("StrategyRunner.analyze");
    }

    public int prune() {
        Set<Faultload> toRemove = new HashSet<>();

        for (var faultload : this.queue) {
            boolean shouldPrune = false;

            for (Pruner pruner : pruners) {
                var decision = pruner.prune(faultload);
                switch (decision) {
                    case KEEP:
                        break;
                    case PRUNE:
                        statistics.incrementPruner(pruner.getClass().getSimpleName(), 1);
                        shouldPrune = true;
                        break;
                    case PRUNE_SUBTREE:
                        statistics.incrementPruner(pruner.getClass().getSimpleName(), 1);
                        new FeedbackContext(this, pruner.getClass(), null).pruneFaultSubset(faultload.faultSet());
                        shouldPrune = true;
                        break;
                    default:
                        break;

                }
            }

            if (shouldPrune) {
                toRemove.add(faultload);
            }
        }

        statistics.incrementPruned(toRemove.size());
        this.queue.removeAll(toRemove);
        return toRemove.size();
    }
}
