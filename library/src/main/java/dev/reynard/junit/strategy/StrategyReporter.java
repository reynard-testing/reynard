package dev.reynard.junit.strategy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import dev.reynard.junit.strategy.components.FeedbackContextProvider;
import dev.reynard.junit.strategy.components.PruneContext;
import dev.reynard.junit.strategy.components.PruneContextProvider;
import dev.reynard.junit.strategy.components.Reporter;
import dev.reynard.junit.strategy.util.Env;
import dev.reynard.junit.strategy.util.Pair;
import dev.reynard.junit.strategy.util.Sets;
import dev.reynard.junit.strategy.util.StringFormat;

public class StrategyReporter {
    public static int DEFAULT_WIDTH = 48;
    private StrategyRunner runner;
    private StrategyStatistics statistics;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DefaultPrettyPrinter printer = new DefaultPrettyPrinter();

    public StrategyReporter(StrategyRunner runner) {
        this.runner = runner;
        this.statistics = runner.statistics;
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

    private Object nsConversions(long ns) {
        Map<String, Object> conv = new LinkedHashMap<>();
        conv.put("ns", ns);
        conv.put("us", ns / 1_000.0);
        conv.put("ms", ns / 1_000_000.0);
        conv.put("s", ns / 1_000_000_000.0);
        return conv;
    }

    private Object nsConversions(double ns) {
        Map<String, Object> conv = new LinkedHashMap<>();
        conv.put("ns", ns);
        conv.put("us", ns / 1_000.0);
        conv.put("ms", ns / 1_000_000.0);
        conv.put("s", ns / 1_000_000_000.0);
        return conv;
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
            statsReport.put("count", values.size());
            statsReport.put("average", nsConversions(average));
            statsReport.put("min", nsConversions(min));
            statsReport.put("max", nsConversions(max));
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
                prunerReport.put("directly_pruned",
                        Map.of(
                                "count", value,
                                "percentage", (double) 100.0 * value / totalGenerated));
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

        String outputTag = Env.getEnv(Env.Keys.OUTPUT_TAG);
        if (runner.hasOutputDir()) {
            Path dir = runner.getOutputDir()
                    .resolve(runner.getContextName())
                    .resolve(outputTag);
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

    public Object reportFailures() {
        List<Map<String, Object>> failures = new ArrayList<>();

        for (var failure : statistics.getFailures()) {
            Map<String, Object> failureReport = new LinkedHashMap<>();
            List<Map<String, Object>> faultload = failure.trackedFaultload.getFaultload().faultSet().stream()
                    .map(x -> {
                        Map<String, Object> faultReport = new LinkedHashMap<>();
                        faultReport.put("point", x.uid().toString());
                        faultReport.put("mode", x.mode().toString());
                        return faultReport;
                    })
                    .toList();
            failureReport.put("faultload", faultload);

            List<Map<String, Object>> behaviour = failure.trace.getReports().stream()
                    .map(x -> {
                        Map<String, Object> behaviourReport = new LinkedHashMap<>();
                        behaviourReport.put("point", x.injectionPoint.toString());
                        behaviourReport.put("response_status", x.response.status);

                        if (x.injectedFault == null) {
                            behaviourReport.put("response_duration_ms", x.response.durationMs);
                            if (x.hasFaultBehaviour()) {
                                behaviourReport.put("fault", x.getFault().mode().toString());
                            }
                        } else {
                            behaviourReport.put("injected_fault", x.getFault().mode().toString());
                        }

                        return behaviourReport;
                    })
                    .toList();

            failureReport.put("observed", behaviour);
            failures.add(failureReport);
        }

        return failures;
    }

    public void report() {
        reportOn(reportSearchSpace(), "search_space");
        reportOn(reportComponents(), "components");
        reportOn(reportPrunerStats(), "pruners");
        reportOn(reportTimingStats(), "timing");
        reportOn(reportFailures(), "failures");

        for (var reporter : runner.getReporters()) {
            reportOnReporter(reporter);
        }
    }
}
