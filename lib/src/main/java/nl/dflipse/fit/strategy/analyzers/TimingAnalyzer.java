package nl.dflipse.fit.strategy.analyzers;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            timings.computeIfAbsent(behaviour, x -> new ArrayList<>()).add(timing);
        });
    }

    @Override
    public Map<String, String> report() {
        StrategyReporter.printNewline();
        StrategyReporter.printHeader("Timing per behaviour", 48, "-");
        NumberFormat formatter = new DecimalFormat("#0.0");  
        for (var entry : timings.entrySet()) {
            var point = entry.getKey();
            var values = entry.getValue();
            var intStream = values.stream().mapToInt(x -> x);
            var average = intStream.average().getAsDouble();
            var max = intStream.max().getAsInt();
            var min = intStream.min().getAsInt();

            StrategyReporter.printNewline();
            StrategyReporter.printKeyValue("Behaviour", point.toString());
            StrategyReporter.printKeyValue("Min (ms)", String.valueOf(min));
            StrategyReporter.printKeyValue("Average (ms)", formatter.format(average));
            StrategyReporter.printKeyValue("Max (ms)", String.valueOf(max));
        }
        // Don't report in the normal sense
        return null;
    }

}
