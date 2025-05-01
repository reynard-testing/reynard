package io.github.delanoflipse.fit.strategy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import io.github.delanoflipse.fit.strategy.components.FeedbackContextProvider;
import io.github.delanoflipse.fit.strategy.components.PruneContext;
import io.github.delanoflipse.fit.strategy.components.PruneContextProvider;
import io.github.delanoflipse.fit.strategy.components.Reporter;
import io.github.delanoflipse.fit.strategy.components.generators.Generator;
import io.github.delanoflipse.fit.strategy.util.Pair;
import io.github.delanoflipse.fit.strategy.util.Sets;
import io.github.delanoflipse.fit.strategy.util.StringFormat;

public class StrategyReporter {
    public static int DEFAULT_WIDTH = 48;
    private Generator generator;
    private StrategyRunner runner;
    private StrategyStatistics statistics;

    public StrategyReporter(StrategyRunner runner) {
        this.runner = runner;
        this.statistics = runner.statistics;
        this.generator = runner.getGenerator();
    }

    public static void printNewline() {
        System.out.println();
    }

    public static void printLine(String line) {
        System.out.println(line);
    }

    public static void printHeader(String header, int width, String padChar) {
        printLine(StringFormat.padBoth(" " + header + " ", width, padChar));
    }

    public static void printHeader(String header, int width) {
        printHeader(header, width, "-");
    }

    private void printHeader(String header) {
        printHeader(header, DEFAULT_WIDTH);
    }

    private static int stringLength(String string) {
        return Arrays.stream(string.split("\n"))
                .mapToInt(String::length)
                .max()
                .orElse(0);
    }

    private static int getMaxLength(Set<String> set) {
        return set.stream()
                .mapToInt(s -> stringLength(s))
                .max().orElse(0);
    }

    public void reportOverall() {
        long totalGenerated = statistics.getTotalGenerated();
        long totalPruned = statistics.getTotalPruned();
        long totalRun = statistics.getTotalRun();
        long faultSpaceSize = statistics.getTotalSize() - 1;

        long fullSpace = faultSpaceSize + 1;
        String prunePercentage = StringFormat.asPercentage(totalPruned, totalGenerated);
        String generatePercentage = StringFormat.asPercentage(totalGenerated, faultSpaceSize);
        String runPercentage = StringFormat.asPercentage(totalRun, fullSpace);
        String reductionPercentage = StringFormat.asPercentage(fullSpace - totalRun, fullSpace);

        Map<String, String> report = new LinkedHashMap<>();
        report.put("Complete space size", fullSpace + " (" + reductionPercentage + "% reduction)");
        report.put("Total generated", totalGenerated + " (" + generatePercentage + "% of space)");
        report.put("Total directly pruned", totalPruned + " (" + prunePercentage + "% of generated)");
        report.put("Total run", totalRun + " (" + runPercentage + "% of full space)");
        printReport("Statistics", report);
    }

    public void reportComponents() {
        printNewline();
        printHeader("Components");
        for (String componentName : runner.getComponentNames()) {
            printLine(componentName);
        }
    }

    public static void printKeyValue(String key, String value, int keyPadding) {
        printLine(StringFormat.padRight(key, keyPadding) + " : " + value);
    }

    public static void printKeyValue(String key, String value) {
        printLine(key + " : " + value);
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
            report.put(tag, getAverageTime(tag) + " (ms)");
        }

        printReport("Timings", report);
    }

    public void reportPrunerStats() {
        Map<String, Long> prunerCount = statistics.getPrunerCount();

        long totalGenerated = statistics.getTotalGenerated();

        printNewline();
        printLine(StringFormat.padBoth(" Pruners ", DEFAULT_WIDTH, "="));

        Set<String> names = Sets.union(prunerCount.keySet(), FeedbackContextProvider.getContextNames());

        for (var contextName : names) {
            Map<String, String> prunerReport = new LinkedHashMap<>();

            if (prunerCount.containsKey(contextName)) {
                long value = prunerCount.get(contextName);
                prunerReport.put("Directly pruned",
                        value + " (" + StringFormat.asPercentage(value, totalGenerated) + "% of generated)");
            }

            if (FeedbackContextProvider.hasContext(contextName)) {
                prunerReport.putAll(FeedbackContextProvider.getReport(contextName, runner.getGenerator()));
            }

            if (prunerReport.size() > 0) {
                printReport(contextName, prunerReport);
            }
        }
        printNewline();
    }

    public static void printReport(String name, Map<String, String> keyValues) {
        int maxKeyLength = getMaxLength(keyValues.keySet());
        int maxValueLength = keyValues.values().stream()
                .mapToInt(s -> stringLength(s))
                .max().orElse(0);
        int maxCharSize = Math.min(DEFAULT_WIDTH, maxKeyLength + maxValueLength + 4);
        printNewline();

        if (name.length() > 0) {
            printHeader(name, maxCharSize);
        }

        for (var entry : keyValues.entrySet()) {
            printKeyValue(entry.getKey(), entry.getValue(), maxKeyLength);
        }
    }

    public void reportOnReporter(Reporter reporter) {
        PruneContext context = new PruneContextProvider(runner, this.getClass());
        Map<String, String> report = reporter.report(context);
        if (report == null || report.isEmpty()) {
            return;
        }
        String name = reporter.getClass().getSimpleName();
        printReport(name, report);
    }

    public void report() {
        reportOverall();
        reportComponents();
        reportGeneratorStats();
        reportPrunerStats();
        reportTimingStats();

        for (var reporter : runner.getReporters()) {
            reportOnReporter(reporter);
        }
        printNewline();
    }
}
