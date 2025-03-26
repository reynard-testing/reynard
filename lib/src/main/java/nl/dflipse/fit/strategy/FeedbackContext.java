package nl.dflipse.fit.strategy;

import java.util.HashMap;
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
        assertGeneratorPresent();
        this.contextName = contextName;
        this.runner = runner;

        // restore store if it exists
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
        runner.getGenerator().reportFaultUids(faultInjectionPoints);
    }

    public void reportConditionalFaultUid(Set<Fault> condition, FaultUid fid) {
        long reduction = store.estimateReductionForConditionalFaultUid(condition, fid);
        store.addConditionalFaultUid(condition, fid);
        runner.getGenerator().reportConditionalFaultUid(condition, fid);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
    }

    public void pruneFaultUidSubset(Set<FaultUid> subset) {
        long reduction = store.estimateReductionForFaultUidSubset(subset);
        store.pruneFaultUidSubset(subset);
        runner.getGenerator().pruneFaultUidSubset(subset);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
    }

    public long pruneFaultSubset(Set<Fault> subset) {
        long reduction = store.estimateReductionForFaultSubset(subset);
        store.pruneFaultSubset(subset);
        runner.getGenerator().pruneFaultSubset(subset);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
        return reduction;
    }

    public void pruneMixed(Set<Fault> subset, FaultUid fault) {
        long sum = 0;
        for (var mode : runner.getGenerator().getFaultModes()) {
            Set<Fault> mixed = Sets.plus(subset, new Fault(fault, mode));
            sum += pruneFaultSubset(mixed);
        }
        runner.statistics.incrementEstimatePruner(contextName, sum);
    }

    public void pruneFaultload(Faultload fautload) {
        long reduction = store.estimateReductionForFaultload(fautload);
        store.pruneFaultload(fautload);
        runner.getGenerator().pruneFaultload(fautload);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
    }
}
