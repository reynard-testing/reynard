package nl.dflipse.fit.strategy.components.generators;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.modes.FailureMode;
import nl.dflipse.fit.strategy.StrategyReporter;
import nl.dflipse.fit.strategy.components.PruneContext;
import nl.dflipse.fit.strategy.components.PruneDecision;
import nl.dflipse.fit.strategy.components.Reporter;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;
import nl.dflipse.fit.strategy.util.Pair;
import nl.dflipse.fit.strategy.util.PrunableGenericPowersetTreeIterator;
import nl.dflipse.fit.strategy.util.Simplify;
import nl.dflipse.fit.strategy.util.SpaceEstimate;
import nl.dflipse.fit.trace.tree.TraceReport;

public class IncreasingSizeGenerator extends Generator implements Reporter {
    private final Logger logger = LoggerFactory.getLogger(IncreasingSizeGenerator.class);
    private final PrunableGenericPowersetTreeIterator iterator;

    private final DynamicAnalysisStore store;

    public IncreasingSizeGenerator(DynamicAnalysisStore store, Function<Set<Fault>, PruneDecision> pruneFunction) {
        this.store = store;
        iterator = new PrunableGenericPowersetTreeIterator(store, pruneFunction, true);
    }

    public IncreasingSizeGenerator(List<FailureMode> modes, Function<Set<Fault>, PruneDecision> pruneFunction) {
        this(new DynamicAnalysisStore(modes), pruneFunction);
    }

    @Override
    public void reportFaultUid(FaultUid point) {
        if (point == null) {
            return;
        }

        boolean isNew = store.addFaultUid(point);
        if (isNew) {
            logger.info("Discover new fault injection point {}", point);
        }
    }

    @Override
    public boolean reportUpstreamEffect(FaultUid cause, Collection<FaultUid> effect) {
        return store.addUpstreamEffect(cause, effect);
    }

    @Override
    public boolean reportPreconditionOfFaultUid(Collection<Behaviour> condition, FaultUid fid) {
        if (fid == null) {
            return true;
        }

        return store.addConditionForFaultUid(condition, fid);
    }

    @Override
    public boolean exploreFrom(Collection<Fault> startingNode) {
        return iterator.expandFrom(startingNode);
    }

    @Override
    public boolean reportExclusionOfFaultUid(Collection<Behaviour> condition, FaultUid exclusion) {
        if (exclusion == null) {
            return true;
        }

        return store.addExclusionForFaultUid(condition, exclusion);
    }

    @Override
    public boolean reportDownstreamEffect(Collection<Behaviour> condition, Behaviour effect) {
        return store.addDownstreamEffect(condition, effect);
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
    public void pruneFaultUidSubset(Set<FaultUid> subset) {
        store.pruneFaultUidSubset(subset);
    }

    @Override
    public void pruneFaultSubset(Set<Fault> subset) {
        store.pruneFaultSubset(subset);
    }

    @Override
    public void pruneFaultload(Faultload faultload) {
        store.pruneFaultload(faultload);
    }

    private int getNumerOfPoints() {
        return store.getPoints().size();
    }

    @Override
    public long spaceSize() {
        return SpaceEstimate.spaceSize(getFailureModes().size(), getNumerOfPoints());
    }

    @Override
    public List<FailureMode> getFailureModes() {
        return store.getModes();
    }

    @Override
    public List<FaultUid> getFaultInjectionPoints() {
        return store.getPoints();
    }

    @Override
    public Set<FaultUid> getExpectedPoints(Set<Fault> faultload) {
        return store.getExpectedPoints(faultload);
    }

    @Override
    public Set<Behaviour> getExpectedBehaviours(Set<Fault> faultload) {
        return store.getExpectedBehaviour(faultload);
    }

    public DynamicAnalysisStore getStore() {
        return store;
    }

    @Override
    public void prune() {
        iterator.pruneQueue();
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
    public Map<String, String> report(PruneContext context) {
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

        StrategyReporter.printReport("Visited (simplified)", simplifiedReport);

        return report;
    }

    @Override
    public List<Pair<Set<Fault>, List<Behaviour>>> getHistoricResults() {
        return store.getHistoricResults();
    }

    @Override
    public Map<FaultUid, TraceReport> getHappyPath() {
        return store.getHappyPath();
    }

    @Override
    public void reportHappyPath(TraceReport report) {
        store.addHappyPath(report.faultUid, report);
    }

}
