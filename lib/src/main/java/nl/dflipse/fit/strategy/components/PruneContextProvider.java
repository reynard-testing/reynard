package nl.dflipse.fit.strategy.components;

import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.modes.FailureMode;
import nl.dflipse.fit.strategy.StrategyRunner;
import nl.dflipse.fit.strategy.util.Pair;

public class PruneContextProvider implements PruneContext {

    private final StrategyRunner runner;

    public PruneContextProvider(StrategyRunner runner, Class<?> clazz) {
        this.runner = runner;
        assertGeneratorPresent();
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

    @Override
    public List<Pair<Set<Fault>, List<Behaviour>>> getHistoricResults() {
        return runner.getGenerator().getHistoricResults();
    }
}
