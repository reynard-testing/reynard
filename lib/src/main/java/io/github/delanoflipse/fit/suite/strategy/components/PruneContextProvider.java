package io.github.delanoflipse.fit.suite.strategy.components;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.faultload.modes.FailureMode;
import io.github.delanoflipse.fit.suite.strategy.StrategyRunner;
import io.github.delanoflipse.fit.suite.strategy.util.Pair;
import io.github.delanoflipse.fit.suite.trace.tree.TraceReport;

public class PruneContextProvider extends PruneContext {

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

    @Override
    public Map<FaultUid, TraceReport> getHappyPath() {
        return runner.getGenerator().getHappyPath();
    }
}
