package nl.dflipse.fit.strategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.pruners.Pruner;

public class StrategyRunner {
    public List<Faultload> queue;
    public HistoricStore history;
    public Set<Faultload> prunedFaultloads;

    public List<FeedbackHandler<Void>> analyzers;
    public List<Generator> generators;
    public List<Pruner> pruners;

    public int prunedCount = 0;
    public int queuedCount = 0;

    public StrategyRunner() {
        history = new HistoricStore();
        prunedFaultloads = new HashSet<>();

        generators = new ArrayList<>();
        pruners = new ArrayList<>();
        analyzers = new ArrayList<>();

        // Initialize the queue with an empty faultload
        queue = new ArrayList<>();
        queue.add(new Faultload());
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

    public Faultload nextFaultload() {
        if (queue.isEmpty()) {
            return null;
        }

        return queue.remove(0);
    }

    public void handleResult(FaultloadResult result) {
        history.add(result);
        analyze(result);

        // Generate new faultloads
        List<Faultload> newFaultloads = generate(result);
        queuedCount += newFaultloads.size();
        // TODO: ignore previously pruned faultloads

        queue.addAll(newFaultloads);

        // Prune the queue
        prune();
    }

    public List<Faultload> generate(FaultloadResult result) {
        List<Faultload> newFaultloads = new ArrayList<>();

        for (Generator generator : generators) {
            newFaultloads.addAll(generator.generate(result));
        }

        return newFaultloads;
    }

    public void analyze(FaultloadResult result) {
        for (var analyzer : analyzers) {
            analyzer.handleFeedback(result, history);
        }
    }

    public void prune() {
        List<Faultload> toRemove = new ArrayList<>();

        for (var faultload : this.queue) {
            for (Pruner pruner : pruners) {
                if (pruner.prune(faultload, history)) {
                    prunedFaultloads.add(faultload);
                    toRemove.add(faultload);
                    break;
                }
            }
        }

        this.prunedCount += toRemove.size();
        this.queue.removeAll(toRemove);
        this.prunedFaultloads.addAll(toRemove);

        if (toRemove.size() > 0) {
            System.out.println("[Strategy] Pruned " + toRemove.size() + " faultloads");
        }
    }
}
