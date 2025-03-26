package nl.dflipse.fit.strategy;

import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.generators.IncreasingSizeGenerator;
import nl.dflipse.fit.strategy.util.Pair;
import nl.dflipse.fit.util.TaggedTimer;

public class StrategyReporter {
    private int maxChars = 32;
    private Generator generator;
    private StrategyRunner runner;
    private StrategyStatistics statistics;

    public StrategyReporter(StrategyRunner runner) {
        this.runner = runner;
        this.statistics = runner.statistics;
        this.generator = runner.getGenerator();
    }

    private void printNewline() {
        System.out.println();
    }

    private void printLine(String line) {
        System.out.println(line);
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

        printNewline();
        printLine(padBoth(" Statistics ", maxChars, "-"));
        printLine("Complete space size : " + fullSpace + " (" + reductionPercentage + "% reduction)");
        printLine("Total generated     : " + totalGenerated + " (" + generatePercentage + "% of space)");
        printLine("Total pruned        : " + totalPruned + " (" + prunePercentage + "% of generated)");
        printLine("Total run           : " + totalRun + " (" + runPercentage + "% of full space)");
    }

    private void printKeyValue(String key, long value, int keyPadding) {
        printKeyValue(key, String.valueOf(value), maxChars);
    }

    private void printKeyValue(String key, String value, int keyPadding) {
        printLine(padRight(key, keyPadding) + " : " + value);
    }

    public void reportGeneratorStats() {
        printNewline();
        printLine(padBoth(" Generator ", maxChars, "-"));
        Map<String, Long> generatorCount = statistics.getGeneratorCount();
        int maxKeyLength = getMaxKeyLength(generatorCount);
        printKeyValue("Generator:", generator.getClass().getSimpleName(), maxKeyLength);
        for (var entry : generatorCount.entrySet()) {
            printKeyValue(entry.getKey(), entry.getValue(), maxKeyLength);
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
        printNewline();
        printLine(padBoth(" Timings ", maxChars, "-"));
        Set<String> tags = statistics.getTags();
        int maxTimingKeyLength = getMaxKeyLength(tags);
        for (String tag : tags) {
            String readableKey = tag.equals(TaggedTimer.DEFAULT_TAG) ? "Total" : tag;
            String key = padRight(readableKey, maxTimingKeyLength);
            printLine(key + " : " + getAverageTime(tag) + " (ms)");
        }
    }

    public void reportPrunerStats() {
        Map<String, Long> prunerCount = statistics.getPrunerCount();
        Map<String, Long> prunerEstimates = statistics.getPrunerEstimates();

        long totalGenerated = statistics.getTotalGenerated();
        long totalSize = statistics.getTotalSize();

        int maxPrunerKeyLength = getMaxKeyLength(prunerCount);
        printNewline();
        printLine(padBoth(" Pruners ", maxChars, "-"));
        for (var entry : prunerCount.entrySet()) {
            String key = padRight(entry.getKey(), maxPrunerKeyLength);
            long value = entry.getValue();
            long estimateValue = prunerEstimates.get(entry.getKey());
            printLine(
                    key + " : " + value + " directly (" + asPercentage(value, totalGenerated) + "% of generated)");
            printLine(padRight("", maxPrunerKeyLength) + " : " + estimateValue + " indirectly ("
                    + asPercentage(estimateValue, totalSize) + "% estimate of space)");
        }
    }

    public void reportOnReporter(Reporter reporter) {
        Map<String, String> report = reporter.report();
        int maxKeyLength = getMaxKeyLength(report.keySet());
        int maxValueLength = report.values().stream().mapToInt(String::length).max().orElse(0);
        int maxCharSize = maxKeyLength + maxValueLength + 5;
        String name = reporter.getClass().getSimpleName();
        printNewline();
        printLine(padBoth(" " + name + " ", maxCharSize, "-"));
        for (var entry : report.entrySet()) {
            printKeyValue(entry.getKey(), entry.getValue(), maxKeyLength);
        }
    }

    public void report() {
        reportOverall();
        reportGeneratorStats();
        reportPrunerStats();
        reportTimingStats();

        for (var reporter : runner.getReporters()) {
            reportOnReporter(reporter);
        }
        printNewline();
    }
}
