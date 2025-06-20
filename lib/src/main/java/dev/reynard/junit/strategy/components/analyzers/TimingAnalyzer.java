package dev.reynard.junit.strategy.components.analyzers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.reynard.junit.faultload.Behaviour;
import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.components.FeedbackContext;
import dev.reynard.junit.strategy.components.FeedbackHandler;
import dev.reynard.junit.strategy.components.PruneContext;
import dev.reynard.junit.strategy.components.Reporter;
import dev.reynard.junit.strategy.util.traversal.TraversalOrder;

public class TimingAnalyzer implements FeedbackHandler, Reporter {
    private final Logger logger = LoggerFactory.getLogger(TimingAnalyzer.class);

    private final Map<Behaviour, List<Float>> responseTimings = new LinkedHashMap<>();
    private final Map<Behaviour, List<Float>> overheadTimings = new LinkedHashMap<>();

    private void addTiming(Map<Behaviour, List<Float>> timings, Behaviour b, float timing) {
        if (timing <= 0) {
            return;
        }

        timings.computeIfAbsent(b, x -> new ArrayList<>()).add(timing);
    }

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        result.trace.traverseReports(TraversalOrder.BREADTH_FIRST, true, report -> {
            var behaviour = report.getBehaviour();
            addTiming(responseTimings, behaviour, report.response.durationMs);
            addTiming(overheadTimings, behaviour, report.response.overheadDurationMs);
        });
    }

    private DoubleStream asDoubleStream(List<Float> v) {
        return v.stream().mapToDouble(i -> i);
    }

    private List<Object> getTimingReport(Map<Behaviour, List<Float>> timings) {
        List<Object> report = new ArrayList<>();

        for (var entry : timings.entrySet()) {
            Map<String, Object> reportEntry = new LinkedHashMap<>();
            var point = entry.getKey();
            var values = entry.getValue();
            var average = asDoubleStream(values).average().getAsDouble();
            var max = asDoubleStream(values).max().getAsDouble();
            var min = asDoubleStream(values).min().getAsDouble();

            reportEntry.put("behaviour", point.toString());
            reportEntry.put("min_ms", min);
            reportEntry.put("average_ms", average);
            reportEntry.put("max_ms", max);
            reportEntry.put("count", values.size());
            reportEntry.put("values", values);

            report.add(reportEntry);
        }

        return report;

    }

    @Override
    public Object report(PruneContext context) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("responses", getTimingReport(responseTimings));
        report.put("overhead", getTimingReport(overheadTimings));
        return report;
    }

}
