package nl.dflipse.fit.strategy;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.util.Sets;

public class FeedbackContext {

    private final String contextName;
    private final StrategyRunner runner;

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

    public Set<FaultUid> getFaultUids() {
        assertGeneratorPresent();
        return runner.generator.getFaultInjectionPoints();
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

    public void reportConditionalFaultUid(Set<Fault> subset, FaultUid fid) {
        assertGeneratorPresent();
        runner.generator.reportConditionalFaultUid(subset, fid);
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

    public void pruneMixed(Set<Fault> subset, FaultUid fault) {
        assertGeneratorPresent();
        long sum = 0;
        for (var mode : runner.generator.getFaultModes()) {
            sum += runner.generator.pruneFaultSubset(Sets.plus(subset, new Fault(fault, mode)));
        }
        runner.statistics.incrementEstimatePruner(contextName, sum);
    }

    public void pruneFaultload(Faultload fautload) {
        assertGeneratorPresent();
        long reduction = runner.generator.pruneFaultload(fautload);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
    }
}
