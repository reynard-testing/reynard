package nl.dflipse.fit.strategy;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;

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

    public Set<FaultMode> getFaultModes() {
        assertGeneratorPresent();
        return runner.generator.getFaultModes();
    }

    public Set<FaultMode> getFaultModes(String type) {
        assertGeneratorPresent();
        return runner.generator.getFaultModes().stream()
                .filter(mode -> mode.getType().equals(type))
                .collect(Collectors.toSet());
    }

    public void reportFaultUids(List<FaultUid> faultInjectionPoints) {
        assertGeneratorPresent();
        runner.generator.reportFaultUids(faultInjectionPoints);
    }

    public void reportConditionalFaultUid(Set<Fault> subset, List<FaultUid> faultInjectionPoints) {
        assertGeneratorPresent();
        runner.generator.reportConditionalFaultUid(subset, faultInjectionPoints);
    }

    public void pruneFaultUidSubset(Set<FaultUid> subset) {
        assertGeneratorPresent();
        long reduction = runner.generator.pruneFaultUidSubset(subset);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
    }

    public void pruneFaultSubset(Set<Fault> subset) {
        assertGeneratorPresent();
        long reduction = runner.generator.pruneFaultSubset(subset);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
    }

    public void pruneFaultload(Faultload fautload) {
        assertGeneratorPresent();
        long reduction = runner.generator.pruneFaultload(fautload);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
    }
}
