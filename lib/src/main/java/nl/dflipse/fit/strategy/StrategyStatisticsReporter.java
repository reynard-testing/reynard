package nl.dflipse.fit.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.generators.IncreasingSizeGenerator;
import nl.dflipse.fit.strategy.generators.IncreasingSizeMixedGenerator;
import nl.dflipse.fit.strategy.util.Pair;
import nl.dflipse.fit.util.TaggedTimer;

public class StrategyStatisticsReporter {
    private int maxChars = 32;
    private Generator generator;
    private StrategyRunner runner;
    private StrategyStatistics statistics;

    public StrategyStatisticsReporter(StrategyRunner runner) {
        this.runner = runner;
        this.statistics = runner.statistics;
        this.generator = runner.generator;
    }

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

    public void reportOverall() {
        long totalGenerated = statistics.getTotalGenerated();
        long totalPruned = statistics.getTotalPruned();
        long totalRun = statistics.getTotalRun();
        long totalSize = statistics.getTotalSize();

        long fullSpace = totalSize + 1;
        String prunePercentage = asPercentage(totalPruned, totalGenerated);
        String generatePercentage = asPercentage(totalGenerated, totalSize);
        String runPercentage = asPercentage(totalRun, fullSpace);
        String reductionPercentage = asPercentage(fullSpace - totalRun, fullSpace);

        System.out.println();
        System.out.println(padBoth(" Statistics ", maxChars, "-"));
        System.out.println("Complete space size : " + fullSpace + " (" + reductionPercentage + "% reduction)");
        System.out.println("Total generated     : " + totalGenerated + " (" + generatePercentage + "% of space)");
        System.out.println("Total pruned        : " + totalPruned + " (" + prunePercentage + "% of generated)");
        System.out.println("Total run           : " + totalRun + " (" + runPercentage + "% of full space)");
    }

    private void printKeyValue(String key, long value, int maxChars) {
        printKeyValue(key, String.valueOf(value), maxChars);
    }

    private void printKeyValue(String key, String value, int maxChars) {
        System.out.println(padRight(key, maxChars) + " : " + value);
    }

    public void reportGenerator(IncreasingSizeMixedGenerator generator, int maxKeyLength) {
        var store = generator.getStore();
        printKeyValue("Fault injection points", generator.getElements(), maxKeyLength);
        printKeyValue("Modes", generator.getFaultModes().size(), maxKeyLength);
        printKeyValue("Redundant faultloads", store.getRedundantFaultloads().size(), maxKeyLength);
        printKeyValue("Redundant fault points", store.getRedundantUidSubsets().size(), maxKeyLength);
        printKeyValue("Redundant fault subsets", store.getRedundantFaultSubsets().size(), maxKeyLength);
    }

    public void reportGenerator(IncreasingSizeGenerator generator, int maxKeyLength) {
        var store = generator.getStore();
        printKeyValue("Fault injection points", generator.getElements(), maxKeyLength);
        printKeyValue("Modes", generator.getFaultModes().size(), maxKeyLength);
        printKeyValue("Redundant faultloads", store.getRedundantFaultloads().size(), maxKeyLength);
        printKeyValue("Redundant fault points", store.getRedundantUidSubsets().size(), maxKeyLength);
        printKeyValue("Redundant fault subsets", store.getRedundantFaultSubsets().size(), maxKeyLength);
    }

    public void reportGeneratorStats() {
        System.out.println();
        System.out.println(padBoth(" Generator ", maxChars, "-"));
        Map<String, Long> generatorCount = statistics.getGeneratorCount();
        int maxKeyLength = getMaxKeyLength(generatorCount);
        printKeyValue("Generator:", generator.getClass().getSimpleName(), maxKeyLength);
        for (var entry : generatorCount.entrySet()) {
            printKeyValue(entry.getKey(), entry.getValue(), maxKeyLength);
        }

        if (generator instanceof IncreasingSizeMixedGenerator) {
            reportGenerator((IncreasingSizeMixedGenerator) generator, maxKeyLength);
        }

        if (generator instanceof IncreasingSizeGenerator) {
            reportGenerator((IncreasingSizeGenerator) generator, maxKeyLength);
        }
    }

    private long getAverageTime(String tag) {
        return (long) statistics.getTimings().stream()
                .filter(entry -> entry.first().equals(tag))
                .mapToLong(Pair::second)
                .average()
                .orElse(0.0);
    }

    public void reportTimingStats() {
        System.out.println();
        System.out.println(padBoth(" Timings ", maxChars, "-"));
        Set<String> tags = statistics.getTags();
        int maxTimingKeyLength = getMaxKeyLength(tags);
        for (String tag : tags) {
            String readableKey = tag.equals(TaggedTimer.DEFAULT_TAG) ? "Total" : tag;
            String key = padRight(readableKey, maxTimingKeyLength);
            System.out.println(key + " : " + getAverageTime(tag) + " (ms)");
        }
    }

    public void reportPrunerStats() {
        Map<String, Long> prunerCount = statistics.getPrunerCount();
        Map<String, Long> prunerEstimates = statistics.getPrunerEstimates();

        long totalGenerated = statistics.getTotalGenerated();
        long totalSize = statistics.getTotalSize();

        int maxPrunerKeyLength = getMaxKeyLength(prunerCount);
        System.out.println();
        System.out.println(padBoth(" Pruners ", maxChars, "-"));
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

    public void report() {
        reportOverall();
        reportGeneratorStats();
        reportPrunerStats();
        reportTimingStats();
    }
}
