package io.github.delanoflipse.fit.suite.strategy.components.analyzers;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.StrategyReporter;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.components.PruneContext;
import io.github.delanoflipse.fit.suite.strategy.components.Reporter;
import io.github.delanoflipse.fit.suite.strategy.util.TraceAnalysis.TraversalStrategy;

public class TimingAnalyzer implements FeedbackHandler, Reporter {
    private final Logger logger = LoggerFactory.getLogger(TimingAnalyzer.class);

    private final Map<Behaviour, List<Float>> timings = new LinkedHashMap<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        result.trace.traverseReports(TraversalStrategy.BREADTH_FIRST, true, report -> {
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
    public Map<String, String> report(PruneContext context) {
        StrategyReporter.printNewline();
        StrategyReporter.printHeader("Timing per behaviour", 48, "-");
        NumberFormat formatter = new DecimalFormat("#0.0");

        // TODO: sort by average
        for (var entry : timings.entrySet()) {
            var point = entry.getKey();
            var values = entry.getValue();
            var average = asDoubleStream(values).average().getAsDouble();
            var max = asDoubleStream(values).max().getAsDouble();
            var min = asDoubleStream(values).min().getAsDouble();

            StrategyReporter.printNewline();
            StrategyReporter.printKeyValue("Behaviour", point.toString() + " (" + values.size() + ")");

            StrategyReporter.printLine("Min (ms): " + formatter.format(min)
                    + "\tAverage (ms): " + formatter.format(average)
                    + "\tMax (ms): " + formatter.format(max));
        }
        // Don't report in the normal sense
        return null;
    }

}
