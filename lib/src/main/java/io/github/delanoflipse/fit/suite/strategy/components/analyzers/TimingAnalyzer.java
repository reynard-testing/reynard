package io.github.delanoflipse.fit.suite.strategy.components.analyzers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.components.PruneContext;
import io.github.delanoflipse.fit.suite.strategy.components.Reporter;
import io.github.delanoflipse.fit.suite.strategy.util.traversal.TraversalOrder;

public class TimingAnalyzer implements FeedbackHandler, Reporter {
    private final Logger logger = LoggerFactory.getLogger(TimingAnalyzer.class);

    private final Map<Behaviour, List<Float>> timings = new LinkedHashMap<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        result.trace.traverseReports(TraversalOrder.BREADTH_FIRST, true, report -> {
            var behaviour = report.getBehaviour();
            float timing = report.response.durationMs;

            if (timing <= 0 || report.injectedFault != null) {
                return;
            }

            timings.computeIfAbsent(behaviour, x -> new ArrayList<>()).add(timing);
        });
    }

    private DoubleStream asDoubleStream(List<Float> v) {
        return v.stream().mapToDouble(i -> i);
    }

    @Override
    public Object report(PruneContext context) {
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

}
