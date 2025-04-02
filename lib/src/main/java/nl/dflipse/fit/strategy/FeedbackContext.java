package nl.dflipse.fit.strategy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.StringFormat;

public class FeedbackContext {

    private final StrategyRunner runner;
    private final DynamicAnalysisStore store;
    private final FaultloadResult result;

    private final static Map<String, DynamicAnalysisStore> stores = new HashMap<>();

    public FeedbackContext(StrategyRunner runner, String contextName, FaultloadResult result) {
        this.runner = runner;
        this.result = result;
        assertGeneratorPresent();
        this.store = stores.computeIfAbsent(contextName,
                k -> new DynamicAnalysisStore(runner.getGenerator().getFaultModes()));
    }

    private void assertGeneratorPresent() {
        if (!runner.hasGenerators()) {
            throw new IllegalStateException("No generator set for runner!");
        }
    }

    public Generator getGenerator() {
        return runner.getGenerator();
    }

    public Set<FaultMode> getFaultModes() {
        return runner.getGenerator().getFaultModes();
    }

    public Set<FaultUid> getFaultUids() {
        return runner.getGenerator().getFaultInjectionPoints();
    }

    public Map<FaultUid, Set<Set<Fault>>> getConditionals() {
        return runner.getGenerator().getConditionalFaultInjectionPoints();
    }

    public Map<FaultUid, Set<Set<Fault>>> getExclusions() {
        return runner.getGenerator().getExclusionsForFaultInjectionPoints();
    }

    public Set<Set<Fault>> getConditions(FaultUid fault) {
        if (fault == null) {
            return Set.of();
        }

        var mapping = getConditionals();
        if (!mapping.containsKey(fault)) {
            return Set.of();
        }

        return mapping.get(fault);
    }

    public Set<FaultUid> getConditionalForFaultload() {
        Set<Fault> injectedFaults = result.trace.getInjectedFaults();
        Set<FaultUid> res = new HashSet<>();
        for (var entry : getConditionals().entrySet()) {
            FaultUid conditional = entry.getKey();
            for (var condition : entry.getValue()) {
                if (Sets.isSubsetOf(injectedFaults, condition)) {
                    res.add(conditional);
                    break;
                }
            }
        }

        return res;
    }

    public Set<FaultUid> getExclusionsForFaultload() {
        Set<Fault> intentedFaults = result.trackedFaultload.getFaultload().faultSet();

        Set<FaultUid> res = new HashSet<>();
        for (var entry : getExclusions().entrySet()) {
            FaultUid conditional = entry.getKey();
            for (var condition : entry.getValue()) {
                if (Sets.isSubsetOf(intentedFaults, condition)) {
                    res.add(conditional);
                    break;
                }
            }
        }

        return res;
    }

    public void reportFaultUids(List<FaultUid> faultInjectionPoints) {
        store.addFaultUids(faultInjectionPoints);
        runner.getGenerator().reportFaultUids(faultInjectionPoints);
    }

    public void reportConditionalFaultUid(Set<Fault> condition, FaultUid fid) {
        store.addConditionForFaultUid(condition, fid);
        runner.getGenerator().reportPreconditionOfFaultUid(condition, fid);
    }

    public void reportExclusionOfFaultUid(Set<Fault> condition, FaultUid fid) {
        store.addExclusionForFaultUid(condition, fid);
        runner.getGenerator().reportExclusionOfFaultUid(condition, fid);
    }

    public void pruneFaultUidSubset(Set<FaultUid> subset) {
        store.pruneFaultUidSubset(subset);
        runner.getGenerator().pruneFaultUidSubset(subset);
    }

    public void pruneFaultSubset(Set<Fault> subset) {
        store.pruneFaultSubset(subset);
        runner.getGenerator().pruneFaultSubset(subset);
    }

    public void pruneMixed(Set<Fault> subset, FaultUid fault) {
        for (var mode : runner.getGenerator().getFaultModes()) {
            Set<Fault> mixed = Sets.plus(subset, new Fault(fault, mode));
            pruneFaultSubset(mixed);
        }
    }

    public void pruneFaultload(Faultload fautload) {
        store.pruneFaultload(fautload);
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
        if (redundantFaultloads.size() > 0) {
            hasImpact = true;
            report.put("Faultloads pruned", redundantFaultloads.size() + "");
        }

        var redundantFaultSubsets = store.getRedundantFaultSubsets();
        if (redundantFaultSubsets.size() > 0) {
            hasImpact = true;
            report.put("Fault subsets pruned", redundantFaultSubsets.size() + "");
            var sizeCount = getDistribution(redundantFaultSubsets);
            for (var entry : sizeCount.entrySet()) {
                report.put("Fault subsets of size " + entry.getKey(), entry.getValue() + "");
            }
        }

        var redundantUidSubsets = store.getRedundantUidSubsets();
        if (redundantUidSubsets.size() > 0) {
            hasImpact = true;
            report.put("Fault points subsets pruned", redundantUidSubsets.size() + "");
            var sizeCount = getDistribution(redundantUidSubsets);
            for (var entry : sizeCount.entrySet()) {
                report.put("Fault points subsets of size " + entry.getKey(), entry.getValue() + "");
            }
        }

        var inclusions = store.getInclusionConditions().getStore();
        if (inclusions.size() > 0) {
            hasImpact = true;
            report.put("Points with inclusion condition", inclusions.size() + "");
            for (var entry : inclusions.entrySet()) {
                report.put(entry.getKey().toString(), entry.getValue().size() + "");
            }
        }

        var exclusions = store.getExclusionConditions().getStore();
        if (exclusions.size() > 0) {
            hasImpact = true;
            report.put("Points with exclusion condition", exclusions.size() + "");
            for (var entry : inclusions.entrySet()) {
                report.put(entry.getKey().toString(), entry.getValue().size() + "");
            }
        }

        if (hasImpact) {
            Set<FaultUid> points = generator.getFaultInjectionPoints();
            long totalSize = generator.spaceSize();
            long estimateValue = store.estimatePruned(points);
            report.put("Indirectly pruned", estimateValue + " ("
                    + StringFormat.asPercentage(estimateValue, totalSize) + "% estimate of space)");
        }

        return report;
    }
}
