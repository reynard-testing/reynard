package nl.dflipse.fit.strategy.analyzers;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.FeedbackHandler;
import nl.dflipse.fit.strategy.Reporter;
import nl.dflipse.fit.strategy.StrategyReporter;
import nl.dflipse.fit.strategy.util.TraceAnalysis.TraversalStrategy;

public class TimingAnalyzer implements FeedbackHandler, Reporter {
    private final Logger logger = LoggerFactory.getLogger(TimingAnalyzer.class);

    private final Map<Behaviour, List<Integer>> timings = new LinkedHashMap<>();

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        result.trace.traverseReports(TraversalStrategy.BREADTH_FIRST, true, report -> {
            var behaviour = report.getBehaviour();
            var timing = report.response.durationMs;

            if (timing == 0 || report.injectedFault != null) {
                return;
            }

            timings.computeIfAbsent(behaviour, x -> new ArrayList<>()).add(timing);
        });
    }

    private IntStream asIntStream(List<Integer> v) {
        return v.stream().mapToInt(i -> i);
    }

    @Override
    public Map<String, String> report() {
        StrategyReporter.printNewline();
        StrategyReporter.printHeader("Timing per behaviour", 48, "-");
        NumberFormat formatter = new DecimalFormat("#0.0");

        // TODO: sort by average
        for (var entry : timings.entrySet()) {
            var point = entry.getKey();
            var values = entry.getValue();
            var average = asIntStream(values).average().getAsDouble();
            var max = asIntStream(values).max().getAsInt();
            var min = asIntStream(values).min().getAsInt();

            StrategyReporter.printNewline();
            StrategyReporter.printKeyValue("Behaviour", point.toString());
            StrategyReporter.printKeyValue("Min (ms)", String.valueOf(min));
            StrategyReporter.printKeyValue("Average (ms)", formatter.format(average) + "(" + values.size() + ")");
            StrategyReporter.printKeyValue("Max (ms)", String.valueOf(max));
        }
        // Don't report in the normal sense
        return null;
    }

}
