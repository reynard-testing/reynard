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

    private long getAverageTime(String tag) {
        return (long) timings.stream()
                .filter(entry -> entry.first().equals(tag))
                .mapToLong(Pair::second)
                .average()
                .orElse(0.0);
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

    private String padRight(String s, int n) {
        return padRight(s, n, " ");
    }

    private String padRight(String s, int n, String character) {
        int toAdd = Math.max(0, n - s.length());
        String padding = character.repeat(toAdd);
        return s + padding;
    }

    private String padLeft(String s, int n) {
        return padLeft(s, n, " ");
    }

    private String padLeft(String s, int n, String character) {
        int toAdd = Math.max(0, n - s.length());
        String padding = character.repeat(toAdd);
        return padding + s;
    }

    private String padBoth(String s, int n) {
        return padBoth(s, n, " ");
    }

    private String padBoth(String s, int n, String character) {
        int toAdd = Math.max(0, n - s.length());
        String padding = character.repeat(toAdd / 2);
        return padding + s + padding;
    }

    private int getMaxKeyLength(Map<String, Long> map) {
        return map.keySet().stream().mapToInt(String::length).max().orElse(0);
    }

    private int getMaxKeyLength(Set<String> set) {
        return set.stream().mapToInt(String::length).max().orElse(0);
    }

    private String asPercentage(long num, long div) {
        double percentage = 100d * num / (double) div;
        return String.format("%1.1f", percentage);
    }

    public void report() {
        int maxWidth = 32;
        long fullSpace = totalSize + 1;
        String prunePercentage = asPercentage(totalPruned, totalGenerated);
        String generatePercentage = asPercentage(totalGenerated, totalSize);
        String runPercentage = asPercentage(totalRun, fullSpace);
        String reductionPercentage = asPercentage(fullSpace - totalRun, fullSpace);

        System.out.println(padBoth(" Stats ", maxWidth, "-"));
        System.out.println("Complete space size : " + fullSpace + " (" + reductionPercentage + "% reduction)");
        System.out.println("Total generated     : " + totalGenerated + " (" + generatePercentage + "% of space)");
        System.out.println("Total pruned        : " + totalPruned + " (" + prunePercentage + "% of generated)");
        System.out.println("Total run           : " + totalRun + " (" + runPercentage + "% of full space)");

        System.out.println();
        System.out.println(padBoth(" Timings ", maxWidth, "-"));
        int maxTimingKeyLength = getMaxKeyLength(tags);
        for (String tag : tags) {
            String readableKey = tag.equals(TaggedTimer.DEFAULT_TAG) ? "Total" : tag;
            String key = padRight(readableKey, maxTimingKeyLength);
            System.out.println(key + " : " + getAverageTime(tag) + " (ms)");
        }

        System.out.println();
        System.out.println(padBoth(" Generators ", maxWidth, "-"));
        int maxGeneratorKeyLength = getMaxKeyLength(generatorCount);
        for (var entry : generatorCount.entrySet()) {
            String key = padRight(entry.getKey(), maxGeneratorKeyLength);
            System.out.println(key + " : " + entry.getValue());
        }

        int maxPrunerKeyLength = getMaxKeyLength(prunerCount);
        System.out.println();
        System.out.println(padBoth(" Pruners ", maxWidth, "-"));
        for (var entry : prunerCount.entrySet()) {
            String key = padRight(entry.getKey(), maxPrunerKeyLength);
            long value = entry.getValue();
            long estimateValue = prunerEstimates.get(entry.getKey());
            System.out.println(
                    key + " : " + value + " directly (" + asPercentage(value, totalGenerated) + "% of generated)");
            System.out.println(padRight("", maxPrunerKeyLength) + " : " + estimateValue + " indirectly ("
                    + asPercentage(estimateValue, totalSize) + "% estimate of space)");
        }
    }
}
