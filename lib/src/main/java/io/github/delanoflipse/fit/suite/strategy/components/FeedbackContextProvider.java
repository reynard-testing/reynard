package io.github.delanoflipse.fit.suite.strategy.components;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.faultload.Faultload;
import io.github.delanoflipse.fit.suite.faultload.modes.FailureMode;
import io.github.delanoflipse.fit.suite.strategy.StrategyReporter;
import io.github.delanoflipse.fit.suite.strategy.StrategyRunner;
import io.github.delanoflipse.fit.suite.strategy.components.generators.Generator;
import io.github.delanoflipse.fit.suite.strategy.store.DynamicAnalysisStore;
import io.github.delanoflipse.fit.suite.strategy.util.Pair;
import io.github.delanoflipse.fit.suite.strategy.util.StringFormat;
import io.github.delanoflipse.fit.suite.trace.tree.TraceReport;

public class FeedbackContextProvider extends FeedbackContext {

    private final StrategyRunner runner;
    private final DynamicAnalysisStore localStore;

    private final static Map<String, DynamicAnalysisStore> stores = new HashMap<>();

    public FeedbackContextProvider(StrategyRunner runner, Class<?> clazz) {
        this.runner = runner;
        assertGeneratorPresent();
        this.localStore = stores.computeIfAbsent(clazz.getSimpleName(),
                k -> new DynamicAnalysisStore(runner.getGenerator().getFailureModes(), true));
    }

    private void assertGeneratorPresent() {
        if (!runner.hasGenerators()) {
            throw new IllegalStateException("No generator set for runner!");
        }
    }

    @Override
    public List<FailureMode> getFailureModes() {
        return runner.getGenerator().getFailureModes();
    }

    @Override
    public List<FaultUid> getFaultInjectionPoints() {
        return runner.getGenerator().getFaultInjectionPoints();
    }

    @Override
    public void reportFaultUid(FaultUid faultInjectionPoint) {
        localStore.addFaultUid(faultInjectionPoint);
        runner.getGenerator().reportFaultUid(faultInjectionPoint);
    }

    @Override
    public boolean reportUpstreamEffect(FaultUid cause, Collection<FaultUid> effect) {
        localStore.addUpstreamEffect(cause, effect);
        return runner.getGenerator().reportUpstreamEffect(cause, effect);
    }

    @Override
    public boolean reportDownstreamEffect(Collection<Behaviour> condition, Behaviour effect) {
        localStore.addDownstreamEffect(condition, effect);
        return runner.getGenerator().reportDownstreamEffect(condition, effect);
    }

    @Override
    public boolean reportPreconditionOfFaultUid(Collection<Behaviour> condition, FaultUid fid) {
        localStore.addConditionForFaultUid(condition, fid);
        return runner.getGenerator().reportPreconditionOfFaultUid(condition, fid);
    }

    @Override
    public boolean reportExclusionOfFaultUid(Collection<Behaviour> condition, FaultUid fid) {
        localStore.addExclusionForFaultUid(condition, fid);
        return runner.getGenerator().reportExclusionOfFaultUid(condition, fid);
    }

    @Override
    public boolean exploreFrom(Collection<Fault> startingNode) {
        return runner.getGenerator().exploreFrom(startingNode);
    }

    @Override
    public boolean exploreFrom(Collection<Fault> startingNode, Collection<FaultUid> combinations) {
        return runner.getGenerator().exploreFrom(startingNode, combinations);
    }

    @Override
    public void pruneFaultUidSubset(Set<FaultUid> subset) {
        localStore.pruneFaultUidSubset(subset);
        runner.getGenerator().pruneFaultUidSubset(subset);
    }

    @Override
    public void pruneFaultSubset(Set<Fault> subset) {
        localStore.pruneFaultSubset(subset);
        runner.getGenerator().pruneFaultSubset(subset);
    }

    @Override
    public void pruneFaultload(Faultload fautload) {
        localStore.pruneFaultload(fautload);
        runner.getGenerator().pruneFaultload(fautload);
    }

    public static Set<String> getContextNames() {
        return stores.keySet();
    }

    public static Map<String, DynamicAnalysisStore> getStores() {
        return stores;
    }

    public static boolean hasContext(String contextName) {
        return stores.containsKey(contextName);
    }

    private static <X> Map<Integer, Integer> getDistribution(List<Set<X>> subsets) {
        Map<Integer, Integer> sizeCount = new HashMap<>();
        for (var subset : subsets) {
            int size = subset.size();
            sizeCount.put(size, sizeCount.getOrDefault(size, 0) + 1);
        }
        return sizeCount;
    }

    public static Map<String, String> getReport(String contextName, Generator generator) {
        if (!hasContext(contextName)) {
            return null;
        }

        Map<String, String> report = new LinkedHashMap<>();
        DynamicAnalysisStore store = stores.get(contextName);
        boolean hasImpact = false;

        var redundantFaultloads = store.getRedundantFaultloads();
        if (!redundantFaultloads.isEmpty()) {
            hasImpact = true;
            report.put("Faultloads pruned", redundantFaultloads.size() + "");
        }

        var redundantFaultSubsets = store.getRedundantFaultSubsets();
        if (!redundantFaultSubsets.isEmpty()) {
            hasImpact = true;
            report.put("Fault subsets pruned", redundantFaultSubsets.size() + "");
            var sizeCount = getDistribution(redundantFaultSubsets);
            for (var entry : sizeCount.entrySet()) {
                report.put("Fault subsets of size " + entry.getKey(), entry.getValue() + "");
            }
        }

        var redundantUidSubsets = store.getRedundantUidSubsets();
        if (!redundantUidSubsets.isEmpty()) {
            hasImpact = true;
            report.put("Fault points subsets pruned", redundantUidSubsets.size() + "");
            var sizeCount = getDistribution(redundantUidSubsets);
            for (var entry : sizeCount.entrySet()) {
                report.put("Fault points subsets of size " + entry.getKey(), entry.getValue() + "");
            }
        }

        report.putAll(store.getImplicationsReport());

        if (hasImpact) {
            List<FaultUid> points = generator.getFaultInjectionPoints();
            long totalSize = generator.spaceSize();
            long estimateValue = store.estimatePruned(points);
            report.put("Indirectly pruned", estimateValue + " ("
                    + StringFormat.asPercentage(estimateValue, totalSize) + "% estimate of space)");
        }

        return report;
    }

    @Override
    public Set<Behaviour> getExpectedBehaviours(Set<Fault> faultload) {
        return runner.getGenerator().getExpectedBehaviours(faultload);
    }

    @Override
    public Set<FaultUid> getExpectedPoints(Set<Fault> faultload) {
        return runner.getGenerator().getExpectedPoints(faultload);
    }

    @Override
    public List<Pair<Set<Fault>, List<Behaviour>>> getHistoricResults() {
        return runner.getGenerator().getHistoricResults();
    }

    @Override
    public Map<FaultUid, TraceReport> getHappyPath() {
        return runner.getGenerator().getHappyPath();
    }

    @Override
    public void reportHappyPath(TraceReport report) {
        localStore.addHappyPath(report.faultUid, report);
        runner.getGenerator().reportHappyPath(report);
    }

    @Override
    public long spaceSize() {
        return runner.getGenerator().spaceSize();
    }

}
