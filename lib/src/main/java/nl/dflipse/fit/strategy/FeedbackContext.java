package nl.dflipse.fit.strategy;

import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;

public class FeedbackContext {

    StrategyRunner runner;

    public FeedbackContext(StrategyRunner runner) {
        this.runner = runner;
    }

    public void reportFaultUids(List<FaultUid> faultInjectionPoints) {
        if (runner.generator == null) {
            throw new IllegalStateException("No generator set for runner!");
        }
        runner.generator.reportFaultUids(faultInjectionPoints);
    }

    public void ignoreFaultUidSubset(Set<FaultUid> subset) {
        if (runner.generator == null) {
            throw new IllegalStateException("No generator set for runner!");
        }
        runner.generator.ignoreFaultUidSubset(subset);
    }

    public void ignoreFaultSubset(Set<Fault> subset) {
        if (runner.generator == null) {
            throw new IllegalStateException("No generator set for runner!");
        }

        runner.generator.ignoreFaultSubset(subset);
    }
}
