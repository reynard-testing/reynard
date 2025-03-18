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
    public List<Faultload> queue;

    public Generator generator = null;

    public List<FeedbackHandler<Void>> analyzers;
    public List<Pruner> pruners;

    public StrategyStatistics statistics = new StrategyStatistics(this);

    private boolean withPayloadMasking = false;
    private long testCasesLeft = -1;

    private final Logger logger = LoggerFactory.getLogger(StrategyRunner.class);

    public StrategyRunner() {
        pruners = new ArrayList<>();
        analyzers = new ArrayList<>();

        // Initialize the queue with an empty faultload
        queue = new ArrayList<>();
        queue.add(new Faultload(Set.of()));
    }

    public StrategyRunner withPayloadMasking() {
        withPayloadMasking = true;
        return this;
    }

    public StrategyRunner withMaxTestCases(long max) {
        testCasesLeft = max;
        return this;
    }

    public StrategyRunner withGenerator(Generator generator) {
        this.generator = generator;
        return this;
    }

    public StrategyRunner withPruner(Pruner pruner) {
        pruners.add(pruner);
        return this;
    }

    public StrategyRunner withAnalyzer(FeedbackHandler<Void> analyzer) {
        analyzers.add(analyzer);
        return this;
    }

    public TrackedFaultload nextFaultload() {
        if (queue.isEmpty()) {
            return null;
        }

        if (testCasesLeft == 0) {
            logger.warn("Reached test case limit, stopping!");
            return null;
        } else if (testCasesLeft > 0) {
            testCasesLeft--;
        }

        var queued = queue.remove(0);
        var tracked = new TrackedFaultload(queued);
        if (withPayloadMasking) {
            tracked.withMaskPayload();
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
            if (queue.size() > 0) {
                break;
            }
        }

        return new Pair<>(generated, pruned);
    }

    public void registerTime(TrackedFaultload faultload) {
        statistics.registerTime(faultload.timer);
    }

    public void handleResult(FaultloadResult result) {
        logger.info("Analyzing result of running faultload with traceId=" + result.faultload.getTraceId());
        analyze(result);
        logger.info("Selecting next faultload");

        result.faultload.timer.start("StrategyRunner.generateAndPrune");
        var res = generateAndPruneTillNext();
        int generated = res.getFirst();
        int pruned = res.getSecond();
        result.faultload.timer.stop("StrategyRunner.generateAndPrune");

        logger.info("Generated " + generated + " new faultloads, pruned " + pruned + " faultloads");
    }

    public List<Faultload> generate() {
        if (generator == null) {
            throw new RuntimeException("[Strategy] No generators are available, make sure to register at least one!");
        }

        var generated = generator.generate();
        String tag = generator.getClass().getSimpleName() + ".generate";
        statistics.incrementGenerator(tag, generated.size());

        return generated;
    }

    @SuppressWarnings("rawtypes")
    public void analyze(FaultloadResult result) {
        result.faultload.timer.start("StrategyRunner.analyze");

        for (var analyzer : analyzers) {
            String name = analyzer.getClass().getSimpleName();
            String tag = name + ".handleFeedback<Analyzer>";
            FeedbackContext context = new FeedbackContext(this, name);
            result.faultload.timer.start(tag);
            analyzer.handleFeedback(result, context);
            result.faultload.timer.stop(tag);
        }

        for (Pruner pruner : pruners) {
            if (pruner instanceof FeedbackHandler) {
                String name = pruner.getClass().getSimpleName();
                String tag = name + ".handleFeedback<Pruner>";
                FeedbackContext context = new FeedbackContext(this, name);
                result.faultload.timer.start(tag);
                ((FeedbackHandler) pruner).handleFeedback(result, context);
                result.faultload.timer.stop(tag);
            }
        }

        if (generator instanceof FeedbackHandler) {
            String name = generator.getClass().getSimpleName();
            String tag = name + ".handleFeedback<Generator>";
            FeedbackContext context = new FeedbackContext(this, name);
            result.faultload.timer.start(tag);
            ((FeedbackHandler) generator).handleFeedback(result, context);
            result.faultload.timer.stop(tag);
        }

        result.faultload.timer.stop("StrategyRunner.analyze");

    }

    public int prune() {
        Set<Faultload> toRemove = new HashSet<>();

        for (var faultload : this.queue) {
            boolean shouldPrune = false;

            for (Pruner pruner : pruners) {
                if (pruner.prune(faultload)) {
                    shouldPrune = true;
                    statistics.incrementPruner(pruner.getClass().getSimpleName(), 1);
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
