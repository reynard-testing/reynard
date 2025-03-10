package nl.dflipse.fit.strategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.pruners.Pruner;
import nl.dflipse.fit.strategy.util.Pair;

public class StrategyRunner {
    public List<Faultload> queue;
    public HistoricStore history;
    public Set<Faultload> prunedFaultloads;

    public List<FeedbackHandler<Void>> analyzers;
    public List<Generator> generators;
    public List<Pruner> pruners;

    public StrategyStatistics statistics = new StrategyStatistics();

    private boolean withPayloadMasking = false;

    public StrategyRunner() {
        history = new HistoricStore();
        prunedFaultloads = new HashSet<>();

        generators = new ArrayList<>();
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

    public StrategyRunner withGenerator(Generator generator) {
        generators.add(generator);
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

            // Keep generating and pruning until we have new faultloads in the queue
            if (queue.size() > 0) {
                break;
            }
        }

        return new Pair<>(generated, pruned);
    }

    public void handleResult(FaultloadResult result) {
        statistics.registerTime(result.faultload.timer);
        history.add(result);
        analyze(result);

        var res = generateAndPruneTillNext();
        int generated = res.getFirst();
        int pruned = res.getSecond();

        System.out.println("[Strategy] Generated " + generated + " new faultloads, pruned " + pruned + " faultloads");
    }

    public List<Faultload> generate() {
        List<Faultload> newFaultloads = new ArrayList<>();

        if (generators.isEmpty()) {
            throw new RuntimeException("[Strategy] No generators are available, make sure to register at least one!");
        }

        // TODO: ignore previously pruned faultloads
        for (Generator generator : generators) {
            var generated = generator.generate();
            statistics.incrementGenerator(generator.getClass().getSimpleName(), generated.size());
            newFaultloads.addAll(generated);
        }

        return newFaultloads;
    }

    @SuppressWarnings("rawtypes")
    public void analyze(FaultloadResult result) {
        for (var analyzer : analyzers) {
            analyzer.handleFeedback(result, history);
        }

        for (Pruner pruner : pruners) {
            if (pruner instanceof FeedbackHandler) {
                ((FeedbackHandler) pruner).handleFeedback(result, history);
            }
        }

        for (Generator gen : generators) {
            if (gen instanceof FeedbackHandler) {
                ((FeedbackHandler) gen).handleFeedback(result, history);
            }
        }
    }

    public int prune() {
        List<Faultload> toRemove = new ArrayList<>();

        for (var faultload : this.queue) {
            boolean shouldPrune = false;

            for (Pruner pruner : pruners) {
                if (pruner.prune(faultload, history)) {
                    shouldPrune = true;
                    statistics.incrementPruner(pruner.getClass().getSimpleName(), 1);
                }
            }

            if (shouldPrune) {
                toRemove.add(faultload);
                prunedFaultloads.add(faultload);
            }
        }

        statistics.incrementPruned(toRemove.size());
        this.queue.removeAll(toRemove);
        this.prunedFaultloads.addAll(toRemove);
        return toRemove.size();
    }
}
