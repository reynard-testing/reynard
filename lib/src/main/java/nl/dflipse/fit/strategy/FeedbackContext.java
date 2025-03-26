package nl.dflipse.fit.strategy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;
import nl.dflipse.fit.strategy.util.Sets;

public class FeedbackContext {

    private final String contextName;
    private final StrategyRunner runner;
    private final DynamicAnalysisStore store;

    private final static Map<String, DynamicAnalysisStore> stores = new HashMap<>();

    public FeedbackContext(StrategyRunner runner, String contextName) {
        this.contextName = contextName;
        this.runner = runner;
        assertGeneratorPresent();
        this.store = stores.computeIfAbsent(contextName,
                k -> new DynamicAnalysisStore(runner.getGenerator().getFaultModes()));

    }

    private void assertGeneratorPresent() {
        if (!runner.hasGenerators()) {
            throw new IllegalStateException("No generator set for runner!");
        }
    }

    public Set<FaultMode> getFaultModes() {
        return runner.getGenerator().getFaultModes();
    }

    public Set<FaultUid> getFaultUids() {
        return runner.getGenerator().getFaultInjectionPoints();
    }

    public void reportFaultUids(List<FaultUid> faultInjectionPoints) {
        store.addFaultUids(faultInjectionPoints);
        runner.getGenerator().reportFaultUids(faultInjectionPoints);
    }

    public void reportConditionalFaultUid(Set<Fault> condition, FaultUid fid) {
        store.addConditionalFaultUid(condition, fid);
        runner.getGenerator().reportConditionalFaultUid(condition, fid);
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

    public static Map<String, String> getReport(String contextName) {
        if (!hasContext(contextName)) {
            return null;
        }

        Map<String, String> report = new LinkedHashMap<>();
        DynamicAnalysisStore store = stores.get(contextName);
        var redundantFaultloads = store.getRedundantFaultloads();
        if (redundantFaultloads.size() > 0) {
            report.put("Faultloads pruned", redundantFaultloads.size() + "");
        }

        var redundantFaultSubsets = store.getRedundantFaultSubsets();
        if (redundantFaultSubsets.size() > 0) {
            report.put("Fault subsets pruned", redundantFaultSubsets.size() + "");
        }

        var redundantUidSubsets = store.getRedundantUidSubsets();
        if (redundantUidSubsets.size() > 0) {
            report.put("Fault points subsets pruned", redundantUidSubsets.size() + "");
        }

        var preconditions = store.getPreconditions();
        if (preconditions.size() > 0) {
            report.put("Preconditions", preconditions.size() + "");
            int count = 0;
            for (var entry : preconditions.entrySet()) {
                count += entry.getValue().size();
            }
            report.put("Precondition subsets", count + "");
        }

        return report;
    }
}
