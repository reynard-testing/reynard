package nl.dflipse.fit.strategy;

import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;

public class FeedbackContext {

    private String contextName;
    private StrategyRunner runner;

    public FeedbackContext(StrategyRunner runner, String contextName) {
        this.contextName = contextName;
        this.runner = runner;
    }

    private void assertGeneratorPresent() {
        if (runner.generator == null) {
            throw new IllegalStateException("No generator set for runner!");
        }
    }

    public void reportFaultUids(List<FaultUid> faultInjectionPoints) {
        assertGeneratorPresent();
        runner.generator.reportFaultUids(faultInjectionPoints);
    }

    public void ignoreFaultUidSubset(Set<FaultUid> subset) {
        assertGeneratorPresent();
        long reduction = runner.generator.ignoreFaultUidSubset(subset);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
    }

    public void ignoreFaultSubset(Set<Fault> subset) {
        assertGeneratorPresent();
        long reduction = runner.generator.ignoreFaultSubset(subset);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
    }

    public void ignoreFaultload(Faultload fautload) {
        assertGeneratorPresent();
        long reduction = runner.generator.ignoreFaultload(fautload);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
    }
}
