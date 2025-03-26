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
        if (!runner.hasGenerators()) {
            throw new IllegalStateException("No generator set for runner!");
        }
    }

    public Set<FaultMode> getFaultModes() {
        assertGeneratorPresent();
        return runner.getGenerator().getFaultModes();
    }

    public Set<FaultUid> getFaultUids() {
        assertGeneratorPresent();
        return runner.getGenerator().getFaultInjectionPoints();
    }

    public void reportFaultUids(List<FaultUid> faultInjectionPoints) {
        assertGeneratorPresent();
        runner.getGenerator().reportFaultUids(faultInjectionPoints);
    }

    public void reportConditionalFaultUid(Set<Fault> subset, FaultUid fid) {
        assertGeneratorPresent();
        runner.getGenerator().reportConditionalFaultUid(subset, fid);
    }

    public void pruneFaultUidSubset(Set<FaultUid> subset) {
        assertGeneratorPresent();
        long reduction = runner.getGenerator().pruneFaultUidSubset(subset);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
    }

    public void pruneFaultSubset(Set<Fault> subset) {
        assertGeneratorPresent();
        long reduction = runner.getGenerator().pruneFaultSubset(subset);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
    }

    public void pruneMixed(Set<Fault> subset, FaultUid fault) {
        assertGeneratorPresent();
        long sum = 0;
        for (var mode : runner.getGenerator().getFaultModes()) {
            sum += runner.getGenerator().pruneFaultSubset(Sets.plus(subset, new Fault(fault, mode)));
        }
        runner.statistics.incrementEstimatePruner(contextName, sum);
    }

    public void pruneFaultload(Faultload fautload) {
        assertGeneratorPresent();
        long reduction = runner.getGenerator().pruneFaultload(fautload);
        runner.statistics.incrementEstimatePruner(contextName, reduction);
    }
}
