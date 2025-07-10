package dev.reynard.junit.strategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.reynard.junit.strategy.util.Pair;
import dev.reynard.junit.util.TaggedTimer;

public class StrategyStatistics {
    private Map<String, Long> generatorCount = new LinkedHashMap<>();
    private Map<String, Long> prunerCount = new LinkedHashMap<>();
    private List<Pair<String, Long>> timings = new ArrayList<>();
    private List<FaultloadResult> failures = new ArrayList<>();
    private Set<String> tags = new LinkedHashSet<>();
    private StrategyRunner runner;

    private long totalRun = 0;
    private long totalSize = 0;
    private long totalGenerated = 0;
    private long totalPruned = 0;

    public StrategyStatistics(StrategyRunner runner) {
        this.runner = runner;
    }

    public void incrementGenerator(String generator, long count) {
        generatorCount.put(generator, generatorCount.getOrDefault(generator, 0L) + count);
        totalGenerated += count;
    }

    public void incrementPruner(String pruner, long count) {
        prunerCount.put(pruner, prunerCount.getOrDefault(pruner, 0L) + count);
    }

    public void incrementPruned(long count) {
        totalPruned += count;
    }

    public void reportFailure(FaultloadResult f) {
        failures.add(f);
    }

    public void setSize(long size) {
        totalSize = size;
    }

    public void registerTime(TaggedTimer timer) {
        for (var entry : timer.getTimingsNs()) {
            timings.add(entry);
            tags.add(entry.first());
        }
    }

    public void registerRun() {
        totalRun++;
    }

    // ---- Helper functions for reporting ----
    public Map<String, Long> getGeneratorCount() {
        return generatorCount;
    }

    public Map<String, Long> getPrunerCount() {
        return prunerCount;
    }

    public List<Pair<String, Long>> getTimings() {
        return timings;
    }

    public Set<String> getTags() {
        return tags;
    }

    public long getTotalRun() {
        return totalRun;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public long getTotalGenerated() {
        return totalGenerated;
    }

    public long getTotalPruned() {
        return totalPruned;
    }

    public List<FaultloadResult> getFailures() {
        return failures;
    }

    public void report() {
        var reporter = new StrategyReporter(runner);
        reporter.report();
    }

    public void reset() {
        generatorCount.clear();
        prunerCount.clear();
        timings.clear();
        tags.clear();
        totalRun = 0;
        totalSize = 0;
        totalGenerated = 0;
        totalPruned = 0;
    }
}
