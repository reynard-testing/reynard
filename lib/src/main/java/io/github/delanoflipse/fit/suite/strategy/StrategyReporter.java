package io.github.delanoflipse.fit.suite.strategy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContextProvider;
import io.github.delanoflipse.fit.suite.strategy.components.PruneContext;
import io.github.delanoflipse.fit.suite.strategy.components.PruneContextProvider;
import io.github.delanoflipse.fit.suite.strategy.components.Reporter;
import io.github.delanoflipse.fit.suite.strategy.components.generators.Generator;
import io.github.delanoflipse.fit.suite.strategy.util.Pair;
import io.github.delanoflipse.fit.suite.strategy.util.Sets;
import io.github.delanoflipse.fit.suite.strategy.util.StringFormat;

public class StrategyReporter {
    public static int DEFAULT_WIDTH = 48;
    private Generator generator;
    private StrategyRunner runner;
    private StrategyStatistics statistics;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DefaultPrettyPrinter printer = new DefaultPrettyPrinter();

    public StrategyReporter(StrategyRunner runner) {
        this.runner = runner;
        this.statistics = runner.statistics;
        this.generator = runner.getGenerator();

        mapper.enable(SerializationFeature.INDENT_OUTPUT);
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

    public Object reportSearchSpace() {
        Map<String, Object> report = new LinkedHashMap<>();
        long totalGenerated = statistics.getTotalGenerated();
        long totalPruned = statistics.getTotalPruned();
        long totalRun = statistics.getTotalRun();
        long faultSpaceSize = statistics.getTotalSize() - 1;

        long fullSpace = faultSpaceSize + 1;

        report.put("faultspace_size", faultSpaceSize);
        report.put("complete_space_size", fullSpace);
        report.put("total_generated", totalGenerated);
        report.put("prune_invocations", totalPruned);
        report.put("total_run", totalRun);
        report.put("cases_run", totalRun - 1);

        return report;
    }

    public Object reportComponents() {
        return runner.getComponentNames();
    }

    public static void printKeyValue(String key, String value, int keyPadding) {
        printLine(StringFormat.padRight(key, keyPadding) + " : " + value);
    }

    public static void printKeyValue(String key, String value) {
        printLine(key + " : " + value);
    }

    private List<Long> getForTag(String tag) {
        return statistics.getTimings().stream()
                .filter(entry -> entry.first().equals(tag))
                .mapToLong(Pair::second)
                .boxed()
                .toList();
    }

    public Object reportTimingStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        Map<String, Object> details = new LinkedHashMap<>();

        for (String tag : statistics.getTags()) {
            List<Long> values = getForTag(tag);
            details.put(tag, values);
            Map<String, Object> statsReport = new LinkedHashMap<>();
            double average = values.stream()
                    .mapToLong(x -> x)
                    .average()
                    .orElse(0.0);
            long max = values.stream()
                    .mapToLong(x -> x)
                    .max()
                    .orElse(0);
            long min = values.stream()
                    .mapToLong(x -> x)
                    .min()
                    .orElse(0);
            statsReport.put("average", average);
            statsReport.put("min", min);
            statsReport.put("max", max);
            statsReport.put("count", values.size());
            stats.put(tag, statsReport);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("stats", stats);
        report.put("details", details);
        return report;
    }

    public Object reportPrunerStats() {
        Map<String, Object> report = new LinkedHashMap<>();
        Map<String, Long> prunerCount = statistics.getPrunerCount();

        long totalGenerated = statistics.getTotalGenerated();

        Set<String> names = Sets.union(prunerCount.keySet(), FeedbackContextProvider.getContextNames());

        for (var contextName : names) {
            Map<String, Object> prunerReport = new LinkedHashMap<>();

            if (prunerCount.containsKey(contextName)) {
                long value = prunerCount.get(contextName);
                prunerReport.put("Directly pruned",
                        value + " (" + StringFormat.asPercentage(value, totalGenerated) + "% of generated)");
            }

            if (FeedbackContextProvider.hasContext(contextName)) {
                prunerReport.putAll(FeedbackContextProvider.getReport(contextName, runner.getGenerator()));
            }

            if (prunerReport.size() > 0) {
                report.put(contextName, prunerReport);
            }
        }

        return report;
    }

    public void reportOnReporter(Reporter reporter) {
        PruneContext context = new PruneContextProvider(runner, this.getClass());
        Object report = reporter.report(context);
        String name = reporter.getClass().getSimpleName();
        reportOn(report, name);
    }

    private void reportOn(Object report, String subcontext) {
        String json;
        try {
            json = mapper.writer(printer)
                    .writeValueAsString(report);
        } catch (JsonProcessingException e) {
            json = e.getMessage();
            json += "\n\n" + report.toString();
        }

        boolean logToConsole = true;

        if (runner.hasOutputDir()) {
            Path dir = runner.getOutputDir().resolve(runner.getContextName());
            dir.toFile().mkdirs();
            Path file = dir.resolve(subcontext + ".json");
            try {
                mapper.writeValue(file.toFile(), report);
                logToConsole = false;
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (logToConsole) {
            printNewline();
            printHeader(subcontext);
            System.out.println(json);
        }
    }

    public void report() {
        reportOn(reportSearchSpace(), "search_space");
        reportOn(reportComponents(), "components");
        reportOn(reportPrunerStats(), "pruners");
        reportOn(reportTimingStats(), "timing");

        for (var reporter : runner.getReporters()) {
            reportOnReporter(reporter);
        }
    }
}
