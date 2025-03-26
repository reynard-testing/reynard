package nl.dflipse.fit.strategy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.util.Pair;
import nl.dflipse.fit.util.TaggedTimer;

public class StrategyReporter {
    private int maxChars = 48;
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

        Map<String, String> report = new LinkedHashMap<>();
        report.put("Complete space size", fullSpace + " (" + reductionPercentage + "% reduction)");
        report.put("Total generated", totalGenerated + " (" + generatePercentage + "% of space)");
        report.put("Total directly pruned", totalPruned + " (" + prunePercentage + "% of generated)");
        report.put("Total run", totalRun + " (" + runPercentage + "% of full space)");
        printReport("Statistics", report);
    }

    private void printKeyValue(String key, String value, int keyPadding) {
        printLine(padRight(key, keyPadding) + " : " + value);
    }

    public void reportGeneratorStats() {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("Generator", generator.getClass().getSimpleName());
        for (var entry : statistics.getGeneratorCount().entrySet()) {
            report.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        printReport("Generator", report);
    }

    private long getAverageTime(String tag) {
        return (long) statistics.getTimings().stream()
                .filter(entry -> entry.first().equals(tag))
                .mapToLong(Pair::second)
                .average()
                .orElse(0.0);
    }

    public void reportTimingStats() {
        Map<String, String> report = new LinkedHashMap<>();

        for (String tag : statistics.getTags()) {
            String readableKey = tag.equals(TaggedTimer.DEFAULT_TAG) ? "Total" : tag;
            report.put(readableKey, getAverageTime(tag) + " (ms)");
        }

        printReport("Timings", report);
    }

    public void reportPrunerStats() {
        Map<String, Long> prunerCount = statistics.getPrunerCount();

        long totalGenerated = statistics.getTotalGenerated();
        long totalSize = statistics.getTotalSize();

        printNewline();
        printLine(padBoth(" Pruners ", maxChars, "="));

        for (var entry : prunerCount.entrySet()) {
            Map<String, String> prunerReport = new LinkedHashMap<>();
            String contextName = entry.getKey();
            long value = entry.getValue();
            prunerReport.put("Directly pruned", value + " (" + asPercentage(value, totalGenerated) + "% of generated)");
            // prunerReport.put("Indirectly pruned", estimateValue + " ("
            // + asPercentage(estimateValue, totalSize) + "% estimate of space)");
            if (FeedbackContext.hasContext(contextName)) {
                prunerReport.putAll(FeedbackContext.getReport(contextName));
            }
            printReport(contextName, prunerReport);
        }
        printNewline();
    }

    public void printReport(String name, Map<String, String> keyValues) {
        int maxKeyLength = getMaxKeyLength(keyValues.keySet());
        int maxValueLength = keyValues.values().stream().mapToInt(String::length).max().orElse(0);
        int maxCharSize = maxKeyLength + maxValueLength + 4;
        printNewline();
        if (name.length() > 0) {
            printLine(padBoth(" " + name + " ", maxCharSize, "-"));
        }
        for (var entry : keyValues.entrySet()) {
            printKeyValue(entry.getKey(), entry.getValue(), maxKeyLength);
        }
    }

    public void reportOnReporter(Reporter reporter) {
        Map<String, String> report = reporter.report();
        String name = reporter.getClass().getSimpleName();
        printReport(name, report);
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
