package nl.dflipse.fit.strategy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.util.Pair;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.StringFormat;
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

    private int getMaxKeyLength(Set<String> set) {
        return set.stream().mapToInt(String::length).max().orElse(0);
    }

    public void reportOverall() {
        long totalGenerated = statistics.getTotalGenerated();
        long totalPruned = statistics.getTotalPruned();
        long totalRun = statistics.getTotalRun();
        long totalSize = statistics.getTotalSize();

        long fullSpace = totalSize + 1;
        String prunePercentage = StringFormat.asPercentage(totalPruned, totalGenerated);
        String generatePercentage = StringFormat.asPercentage(totalGenerated, totalSize);
        String runPercentage = StringFormat.asPercentage(totalRun, fullSpace);
        String reductionPercentage = StringFormat.asPercentage(fullSpace - totalRun, fullSpace);

        Map<String, String> report = new LinkedHashMap<>();
        report.put("Complete space size", fullSpace + " (" + reductionPercentage + "% reduction)");
        report.put("Total generated", totalGenerated + " (" + generatePercentage + "% of space)");
        report.put("Total directly pruned", totalPruned + " (" + prunePercentage + "% of generated)");
        report.put("Total run", totalRun + " (" + runPercentage + "% of full space)");
        printReport("Statistics", report);
    }

    private void printKeyValue(String key, String value, int keyPadding) {
        printLine(StringFormat.padRight(key, keyPadding) + " : " + value);
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

        printNewline();
        printLine(StringFormat.padBoth(" Pruners ", maxChars, "="));

        Set<String> names = Sets.union(prunerCount.keySet(), FeedbackContext.getContextNames());

        for (var contextName : names) {
            Map<String, String> prunerReport = new LinkedHashMap<>();

            if (prunerCount.containsKey(contextName)) {
                long value = prunerCount.get(contextName);
                prunerReport.put("Directly pruned",
                        value + " (" + StringFormat.asPercentage(value, totalGenerated) + "% of generated)");
            }

            if (FeedbackContext.hasContext(contextName)) {
                prunerReport.putAll(FeedbackContext.getReport(contextName, runner.getGenerator()));
            }

            if (prunerReport.size() > 0) {
                printReport(contextName, prunerReport);
            }
        }
        printNewline();
    }

    public void printReport(String name, Map<String, String> keyValues) {
        int maxKeyLength = getMaxKeyLength(keyValues.keySet());
        int maxValueLength = keyValues.values().stream().mapToInt(String::length).max().orElse(0);
        int maxCharSize = maxKeyLength + maxValueLength + 4;
        printNewline();
        if (name.length() > 0) {
            printLine(StringFormat.padBoth(" " + name + " ", maxCharSize, "-"));
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
