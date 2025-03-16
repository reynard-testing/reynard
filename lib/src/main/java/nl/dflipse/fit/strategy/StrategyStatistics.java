package nl.dflipse.fit.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.strategy.util.Pair;
import nl.dflipse.fit.util.TaggedTimer;

public class StrategyStatistics {
    private Map<String, Long> generatorCount = new HashMap<>();
    private Map<String, Long> prunerCount = new HashMap<>();
    private Map<String, Long> prunerEstimates = new HashMap<>();
    private List<Pair<String, Long>> timings = new ArrayList<>();
    private Set<String> tags = new HashSet<>();

    private long totalRun = 0;
    private long totalSize = 0;
    private long totalGenerated = 0;
    private long totalPruned = 0;

    public void incrementGenerator(String generator, long count) {
        generatorCount.put(generator, generatorCount.getOrDefault(generator, 0L) + count);
        totalGenerated += count;
    }

    public void incrementPruner(String pruner, long count) {
        prunerCount.put(pruner, prunerCount.getOrDefault(pruner, 0L) + count);
        if (!prunerEstimates.containsKey(pruner)) {
            prunerEstimates.put(pruner, 0L);
        }
    }

    public void incrementEstimatePruner(String pruner, long count) {
        prunerEstimates.put(pruner, prunerEstimates.getOrDefault(pruner, 0L) + count);
        if (!prunerCount.containsKey(pruner)) {
            prunerCount.put(pruner, 0L);
        }
    }

    public void incrementPruned(long count) {
        totalPruned += count;
    }

    public void setSize(long size) {
        totalSize = size;
    }

    public void registerTime(TaggedTimer timer) {
        for (var entry : timer.getTimings()) {
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

    public Map<String, Long> getPrunerEstimates() {
        return prunerEstimates;
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

    public void report(Generator generator) {
        var reporter = new StrategyStatisticsReporter(this);
        reporter.report(generator);
    }

    public void reset() {
        generatorCount.clear();
        prunerCount.clear();
        prunerEstimates.clear();
        timings.clear();
        tags.clear();
        totalRun = 0;
        totalSize = 0;
        totalGenerated = 0;
        totalPruned = 0;
    }
}
