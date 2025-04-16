package nl.dflipse.fit.strategy;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.modes.FailureMode;
import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;
import nl.dflipse.fit.strategy.util.StringFormat;

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
    public void reportUpstreamEffect(FaultUid cause, Collection<FaultUid> effect) {
        localStore.addUpstreamEffect(cause, effect);
        runner.getGenerator().reportUpstreamEffect(cause, effect);
    }

    @Override
    public void reportDownstreamEffect(Collection<Behaviour> condition, Behaviour effect) {
        localStore.addDownstreamEffect(condition, effect);
        runner.getGenerator().reportDownstreamEffect(condition, effect);
    }

    @Override
    public void reportPreconditionOfFaultUid(Collection<Behaviour> condition, FaultUid fid,
            Collection<Fault> rootCauses) {
        localStore.addConditionForFaultUid(condition, fid);
        runner.getGenerator().reportPreconditionOfFaultUid(condition, fid, rootCauses);
    }

    @Override
    public void reportExclusionOfFaultUid(Collection<Behaviour> condition, FaultUid fid) {
        localStore.addExclusionForFaultUid(condition, fid);
        runner.getGenerator().reportExclusionOfFaultUid(condition, fid);
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
    public long spaceSize() {
        return runner.getGenerator().spaceSize();
    }
}
