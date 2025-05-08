package io.github.delanoflipse.fit.suite.strategy.components.generators;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.faultload.Faultload;
import io.github.delanoflipse.fit.suite.faultload.modes.FailureMode;
import io.github.delanoflipse.fit.suite.strategy.StrategyReporter;
import io.github.delanoflipse.fit.suite.strategy.components.PruneContext;
import io.github.delanoflipse.fit.suite.strategy.components.PruneDecision;
import io.github.delanoflipse.fit.suite.strategy.components.Reporter;
import io.github.delanoflipse.fit.suite.strategy.store.DynamicAnalysisStore;
import io.github.delanoflipse.fit.suite.strategy.util.DynamicPowersetTree;
import io.github.delanoflipse.fit.suite.strategy.util.Simplify;

public class IncreasingSizeGenerator extends StoreBasedGenerator implements Reporter {
    private final Logger logger = LoggerFactory.getLogger(IncreasingSizeGenerator.class);
    private final DynamicPowersetTree iterator;

    public IncreasingSizeGenerator(DynamicAnalysisStore store, Function<Set<Fault>, PruneDecision> pruneFunction) {
        super(store);
        iterator = new DynamicPowersetTree(store, pruneFunction, true);
    }

    public IncreasingSizeGenerator(List<FailureMode> modes, Function<Set<Fault>, PruneDecision> pruneFunction) {
        this(new DynamicAnalysisStore(modes), pruneFunction);
    }

    public int getMaxQueueSize() {
        return iterator.getMaxQueueSize();
    }

    public int getQueuSize() {
        return iterator.getQueuSize();
    }

    public long getSpaceLeft() {
        return iterator.getQueueSpaceSize();
    }

    @Override
    public boolean exploreFrom(Collection<Fault> startingNode) {
        return iterator.expandFrom(startingNode);
    }

    @Override
    public Faultload generate() {
        var nextFaults = iterator.next();

        if (nextFaults == null) {
            return null;
        }

        return new Faultload(nextFaults);
    }

    @Override
    public void prune() {
        iterator.pruneQueue();
    }

    @Override
    public Object report(PruneContext context) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("Fault injection points", String.valueOf(getNumerOfPoints()));
        report.put("Modes", String.valueOf(getFailureModes().size()));
        report.put("Redundant faultloads", String.valueOf(store.getRedundantFaultloads().size()));
        report.put("Redundant fault points", String.valueOf(store.getRedundantUidSubsets().size()));
        report.put("Redundant fault subsets", String.valueOf(store.getRedundantFaultSubsets().size()));
        report.put("Max queue size", String.valueOf(getMaxQueueSize()));
        int queueSize = getQueuSize();
        if (queueSize > 0) {
            report.put("Queue size (left)", String.valueOf(getQueuSize()));
            report.put("Space left", String.valueOf(getSpaceLeft()));
        }

        int i = 0;
        for (var point : getFaultInjectionPoints()) {
            report.put("FID(" + i + ")", point.toString());
            i++;
        }

        // Report the visited faultloads
        var faultloads = store.getHistoricResults().stream()
                .map(x -> x.first())
                .toList();

        // Unique visited
        Map<String, String> simplifiedReport = new LinkedHashMap<>();
        var simplified = Simplify.simplify(faultloads, getFailureModes());
        var failureModesCount = getFailureModes().size();
        int simpleIndex = 1;
        for (Set<FaultUid> cause : simplified.second()) {
            int start = simpleIndex;
            int increase = (int) Math.pow(failureModesCount, cause.size());
            simpleIndex += increase;
            String key = "[" + start + " - " + (start + increase - 1) + " (+" + increase + ")" + "]";
            if (cause.isEmpty()) {
                simplifiedReport.put(key, "(initial empty set)");
                continue;
            }

            simplifiedReport.put(key, cause.toString() + " (all modes)");
        }

        for (Set<Fault> cause : simplified.first()) {
            simplifiedReport.put("[" + simpleIndex + "]", cause.toString());
            simpleIndex++;
        }

        return report;
    }

}
